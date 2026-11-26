package com.gongbang.post.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.gongbang.application.service.ApplicationCommandService;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * INT-005 초대 수락(joinByInvitation)을 실제 MySQL에서 확인한다 (AGENTS.md 7 「동시 요청 테스트」). 초대 수락 서비스(김건우)처럼 READ
 * COMMITTED 트랜잭션으로 부른다.
 *
 * <p>환경 변수 TEST_DATASOURCE_URL이 있을 때만 돈다 (ApplicationAcceptMysqlTest와 같은 방식). 반드시 테스트 전용 스키마(이름에
 * _test)를 가리키게 한다. 테스트가 끝나면 이 테스트가 넣은 회원·공모전·모집글·지원·팀·채팅방·알림 행을 지운다.
 */
@SpringBootTest(
        properties = {
            // test application.yml이 끈 DataSource·JPA·Flyway 자동 설정을 이 테스트에서만 다시 켠다.
            "spring.autoconfigure.exclude=",
            "spring.datasource.url=${TEST_DATASOURCE_URL:}",
            "spring.datasource.username=${TEST_DATASOURCE_USERNAME:root}",
            "spring.datasource.password=${TEST_DATASOURCE_PASSWORD:}",
            "spring.jpa.properties.hibernate.show_sql=false"
        })
@EnabledIfEnvironmentVariable(named = "TEST_DATASOURCE_URL", matches = ".*_test.*")
class TeamJoinInvitationMysqlTest {

    // 다른 테스트 데이터와 겹치지 않게 큰 번호를 쓴다
    private static final long AUTHOR_ID = 960_001L;
    private static final long APPLICANT_ID = 960_011L;
    private static final long INVITEE_ID = 960_012L;
    private static final String MEMBERS = "(960001, 960011, 960012)";

    @Autowired private TeamJoinService teamJoinService;
    @Autowired private ApplicationCommandService applicationCommandService;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    private long contestId;
    private long postId;

    @BeforeEach
    void setUp() {
        for (long memberId : List.of(AUTHOR_ID, APPLICANT_ID, INVITEE_ID)) {
            jdbcTemplate.update(
                    "INSERT INTO member (id, nickname, status) VALUES (?, ?, 'ACTIVE')",
                    memberId,
                    "t" + memberId);
        }
        jdbcTemplate.update(
                "INSERT INTO contest (source_url, source_site, title, category_id, end_date)"
                        + " VALUES ('https://test.local/invite-mysql', 'WEVITY', '초대 수락 공모전', 1,"
                        + " '2099-12-31')");
        contestId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        // 목표 2명(작성자 포함): 남은 자리가 하나
        jdbcTemplate.update(
                "INSERT INTO recruit_post (author_id, contest_id, title, content, target_count,"
                        + " recruit_start_date, recruit_end_date)"
                        + " VALUES (?, ?, '초대 수락 테스트 모집글', '내용', 2, '2026-11-01', '2099-12-01')",
                AUTHOR_ID,
                contestId);
        postId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbcTemplate.update(
                "INSERT INTO application (post_id, applicant_id) VALUES (?, ?)",
                postId,
                APPLICANT_ID);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM email_outbox WHERE receiver_id IN " + MEMBERS);
        jdbcTemplate.update("DELETE FROM notification WHERE receiver_id IN " + MEMBERS);
        String teamRooms =
                "SELECT cr.id FROM chat_room cr JOIN team t ON cr.team_id = t.id WHERE t.post_id = ?";
        jdbcTemplate.update(
                "DELETE FROM chat_message WHERE chat_room_id IN (SELECT id FROM ("
                        + teamRooms
                        + ") r)",
                postId);
        jdbcTemplate.update(
                "DELETE FROM chat_room_member WHERE chat_room_id IN (SELECT id FROM ("
                        + teamRooms
                        + ") r)",
                postId);
        jdbcTemplate.update(
                "DELETE cr FROM chat_room cr JOIN team t ON cr.team_id = t.id WHERE t.post_id = ?",
                postId);
        jdbcTemplate.update(
                "DELETE tm FROM team_member tm JOIN team t ON tm.team_id = t.id WHERE t.post_id = ?",
                postId);
        jdbcTemplate.update(
                "DELETE ce FROM calendar_event ce JOIN team t ON ce.team_id = t.id WHERE t.post_id = ?",
                postId);
        jdbcTemplate.update("DELETE FROM team WHERE post_id = ?", postId);
        jdbcTemplate.update("DELETE FROM application WHERE post_id = ?", postId);
        jdbcTemplate.update("DELETE FROM recruit_post WHERE id = ?", postId);
        jdbcTemplate.update("DELETE FROM contest WHERE id = ?", contestId);
        jdbcTemplate.update("DELETE FROM member WHERE id IN " + MEMBERS);
    }

    // 초대 수락 서비스와 같이 READ COMMITTED 트랜잭션으로 부른다
    private void acceptInvitation(long memberId) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        tx.executeWithoutResult(
                status -> teamJoinService.joinByInvitation(postId, memberId, LocalDateTime.now()));
    }

    private String applicationStatus() {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM application WHERE post_id = ? AND applicant_id = ?",
                String.class,
                postId,
                APPLICANT_ID);
    }

    @Test
    @DisplayName("대기 지원이 있는 회원이 초대를 수락하면 지원이 「수락」이 되고 팀원이 되며, 정원이 차 모집완료된다 (INT-005 ①②③)")
    void 대기_지원자가_초대를_수락하면_지원도_수락() {
        acceptInvitation(APPLICANT_ID);

        assertThat(applicationStatus()).isEqualTo("ACCEPTED");
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT current_count FROM recruit_post WHERE id = ?",
                                Integer.class,
                                postId))
                .isEqualTo(2);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT status FROM recruit_post WHERE id = ?",
                                String.class,
                                postId))
                .isEqualTo("COMPLETED");
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM team_member tm JOIN team t ON tm.team_id = t.id"
                                        + " WHERE t.post_id = ? AND tm.member_id = ? AND tm.left_at IS NULL",
                                Integer.class,
                                postId,
                                APPLICANT_ID))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "마지막 한 자리에 지원 수락과 초대 수락이 동시에 들어오면 하나만 성공하고 다른 하나는 POST_FULL (INT-005 ②, REC-004 ④)")
    void 지원_수락과_초대_수락이_동시에_들어오면_하나만() throws Exception {
        long applicationId =
                jdbcTemplate.queryForObject(
                        "SELECT id FROM application WHERE post_id = ?", Long.class, postId);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger successes = new AtomicInteger();
        ConcurrentLinkedQueue<ErrorCode> businessErrors = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<Throwable> unexpected = new ConcurrentLinkedQueue<>();

        List<Runnable> actions =
                List.of(
                        () -> applicationCommandService.accept(AUTHOR_ID, applicationId),
                        () -> acceptInvitation(INVITEE_ID));
        for (Runnable action : actions) {
            executor.submit(
                    () -> {
                        ready.countDown();
                        try {
                            start.await();
                            action.run();
                            successes.incrementAndGet();
                        } catch (BusinessException e) {
                            businessErrors.add(e.getErrorCode());
                        } catch (Throwable e) {
                            unexpected.add(e);
                        } finally {
                            done.countDown();
                        }
                    });
        }
        ready.await(10, TimeUnit.SECONDS);
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        assertThat(unexpected).isEmpty();
        assertThat(successes.get()).isEqualTo(1);
        // 지원 수락이 먼저면 늦은 초대 수락은 POST_FULL·POST_COMPLETED.
        // 초대 수락이 먼저면 정원이 차서 남은 대기 지원이 자동 거절되므로, 늦은 지원 수락은 ALREADY_PROCESSED (REC-004 ①, REC-006 ④)
        ErrorCode lateError = businessErrors.peek();
        assertThat(businessErrors).hasSize(1);
        if (lateError == ErrorCode.ALREADY_PROCESSED) {
            assertThat(applicationStatus()).isEqualTo("REJECTED");
        } else {
            assertThat(lateError).isIn(ErrorCode.POST_FULL, ErrorCode.POST_COMPLETED);
            assertThat(applicationStatus()).isEqualTo("ACCEPTED");
        }
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT current_count FROM recruit_post WHERE id = ?",
                                Integer.class,
                                postId))
                .isEqualTo(2);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM team_member tm JOIN team t ON tm.team_id = t.id"
                                        + " WHERE t.post_id = ? AND tm.left_at IS NULL",
                                Integer.class,
                                postId))
                .isEqualTo(2);
    }
}
