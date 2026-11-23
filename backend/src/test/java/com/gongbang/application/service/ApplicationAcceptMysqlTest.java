package com.gongbang.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import java.util.List;
import java.util.Map;
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

/**
 * REC-004 지원자 수락을 실제 MySQL에서 동시에 보내 모집글 행 잠금을 확인한다 (AGENTS.md 7 「동시 요청 테스트」).
 *
 * <p>환경 변수 TEST_DATASOURCE_URL이 있을 때만 돈다 (TeamMysqlTest와 같은 방식). 반드시 테스트 전용 스키마(이름에 _test)를 가리키게
 * 한다. 테스트가 끝나면 이 테스트가 넣은 회원·공모전·모집글·지원·팀·채팅방·알림 행을 지운다.
 *
 * <pre>
 * TEST_DATASOURCE_URL=jdbc:mysql://localhost:3306/gongbang_test?serverTimezone=Asia/Seoul
 * </pre>
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
class ApplicationAcceptMysqlTest {

    // 다른 테스트 데이터와 겹치지 않게 큰 번호를 쓴다
    private static final long AUTHOR_ID = 910_001L;
    private static final List<Long> APPLICANT_IDS = List.of(910_011L, 910_012L, 910_013L);
    private static final List<Long> MEMBER_IDS = List.of(AUTHOR_ID, 910_011L, 910_012L, 910_013L);

    @Autowired private ApplicationCommandService applicationCommandService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private long contestId;
    private long postId;

    @BeforeEach
    void setUp() {
        for (Long memberId : MEMBER_IDS) {
            // 알림을 받으려면 활성 회원이어야 한다(NotificationService). 이메일은 비워 메일 대기열에는 넣지 않는다.
            jdbcTemplate.update(
                    "INSERT INTO member (id, nickname, status) VALUES (?, ?, 'ACTIVE')",
                    memberId,
                    "t" + memberId);
        }
        jdbcTemplate.update(
                "INSERT INTO contest (source_url, source_site, title, category_id, end_date)"
                        + " VALUES ('https://test.local/accept-mysql', 'WEVITY', '동시 수락 공모전', 1,"
                        + " '2099-12-31')");
        contestId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbcTemplate.update(
                "INSERT INTO recruit_post (author_id, contest_id, title, content, target_count,"
                        + " recruit_start_date, recruit_end_date)"
                        + " VALUES (?, ?, '동시 수락 테스트 모집글', '내용', 3, '2026-11-01', '2099-12-01')",
                AUTHOR_ID,
                contestId);
        postId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        for (Long applicantId : APPLICANT_IDS) {
            jdbcTemplate.update(
                    "INSERT INTO application (post_id, applicant_id) VALUES (?, ?)",
                    postId,
                    applicantId);
        }
    }

    @AfterEach
    void tearDown() {
        String members = "(910001, 910011, 910012, 910013)";
        jdbcTemplate.update("DELETE FROM email_outbox WHERE receiver_id IN " + members);
        jdbcTemplate.update("DELETE FROM notification WHERE receiver_id IN " + members);
        // 첫 수락 때 팀 채팅방이 함께 생긴다 (INT-006 ①, #99). 채팅 행이 team·member를 가리키므로 먼저 지운다
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
        jdbcTemplate.update("DELETE FROM team WHERE post_id = ?", postId);
        jdbcTemplate.update("DELETE FROM application WHERE post_id = ?", postId);
        jdbcTemplate.update("DELETE FROM recruit_post WHERE id = ?", postId);
        jdbcTemplate.update("DELETE FROM contest WHERE id = ?", contestId);
        jdbcTemplate.update("DELETE FROM member WHERE id IN " + members);
    }

    @Test
    @DisplayName("남은 2자리에 수락 3건이 동시에 들어와도 2건만 성공하고, 정원이 차면 모집완료·남은 지원 자동 거절 (REC-004 ④⑤)")
    void 동시_수락은_정원만큼만_성공한다() throws Exception {
        List<Long> applicationIds =
                jdbcTemplate.queryForList(
                        "SELECT id FROM application WHERE post_id = ? ORDER BY id",
                        Long.class,
                        postId);
        int threads = applicationIds.size();
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger successes = new AtomicInteger();
        ConcurrentLinkedQueue<ErrorCode> businessErrors = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<Throwable> unexpected = new ConcurrentLinkedQueue<>();

        for (Long applicationId : applicationIds) {
            executor.submit(
                    () -> {
                        ready.countDown();
                        try {
                            start.await();
                            applicationCommandService.accept(AUTHOR_ID, applicationId);
                            successes.incrementAndGet();
                        } catch (BusinessException e) {
                            businessErrors.add(e.getErrorCode());
                        } catch (Throwable t) {
                            unexpected.add(t);
                        } finally {
                            done.countDown();
                        }
                    });
        }
        ready.await();
        start.countDown();
        assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        assertThat(unexpected).isEmpty();
        assertThat(successes.get()).isEqualTo(2);
        // 늦게 온 1건은 앞선 수락이 모집완료하며 자동 거절해 두었으므로 ALREADY_PROCESSED.
        // (잠금 순서상 POST_FULL이 나올 수도 있어 둘 다 정원 초과 실패로 본다)
        assertThat(businessErrors)
                .singleElement()
                .isIn(ErrorCode.ALREADY_PROCESSED, ErrorCode.POST_FULL);

        Map<String, Object> post =
                jdbcTemplate.queryForMap(
                        "SELECT current_count, status, completed_at FROM recruit_post WHERE id = ?",
                        postId);
        assertThat(((Number) post.get("current_count")).intValue()).isEqualTo(3);
        assertThat(post.get("status")).isEqualTo("COMPLETED");
        assertThat(post.get("completed_at")).isNotNull();

        assertThat(
                        jdbcTemplate.queryForList(
                                "SELECT CONCAT(status, '/', COALESCE(reject_type, '-'))"
                                        + " FROM application WHERE post_id = ? ORDER BY status",
                                String.class,
                                postId))
                .containsExactlyInAnyOrder("ACCEPTED/-", "ACCEPTED/-", "REJECTED/AUTO");

        Long teamCount =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM team WHERE post_id = ?", Long.class, postId);
        assertThat(teamCount).isEqualTo(1);
        List<Map<String, Object>> teamMembers =
                jdbcTemplate.queryForList(
                        "SELECT tm.member_id, tm.role, tm.color_index FROM team_member tm"
                                + " JOIN team t ON tm.team_id = t.id"
                                + " WHERE t.post_id = ? AND tm.left_at IS NULL ORDER BY tm.color_index",
                        postId);
        assertThat(teamMembers).hasSize(3);
        // BIGINT UNSIGNED는 BigInteger로 오므로 숫자로 바꿔 비교한다
        assertThat(((Number) teamMembers.get(0).get("member_id")).longValue()).isEqualTo(AUTHOR_ID);
        assertThat(teamMembers.get(0).get("role")).isEqualTo("LEADER");
        assertThat(teamMembers)
                .extracting(row -> ((Number) row.get("color_index")).intValue())
                .containsExactly(1, 2, 3);

        // 수락 알림 2건, 자동 거절 알림 1건 (INT-003 ②③)
        assertThat(
                        jdbcTemplate.queryForList(
                                "SELECT type FROM notification WHERE target_type = 'POST' AND target_id = ?",
                                String.class,
                                postId))
                .containsExactlyInAnyOrder("APPLY_ACCEPTED", "APPLY_ACCEPTED", "APPLY_REJECTED");
    }
}
