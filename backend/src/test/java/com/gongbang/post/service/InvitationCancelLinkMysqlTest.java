package com.gongbang.post.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.gongbang.application.service.ApplicationCommandService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 모집글 삭제·모집 완료(수동·자동)·팀 삭제(WS-08) 때 대기 중인 팀 초대가 실제 MySQL에서 CANCELED로 바뀌는지 확인한다 (PST-004 ④, REC-006,
 * INT-005 ④).
 *
 * <p>환경 변수 TEST_DATASOURCE_URL이 있을 때만 돈다 (ApplicationAcceptMysqlTest와 같은 방식). 반드시 테스트 전용 스키마(이름에
 * _test)를 가리키게 한다. 테스트가 끝나면 이 테스트가 넣은 행을 지운다.
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
class InvitationCancelLinkMysqlTest {

    // 다른 테스트 데이터와 겹치지 않게 큰 번호를 쓴다
    private static final long AUTHOR_ID = 980_001L;
    private static final long APPLICANT_ID = 980_011L;
    private static final long INVITEE_ID = 980_012L;
    private static final long OTHER_INVITEE_ID = 980_013L;
    private static final String MEMBERS = "(980001, 980011, 980012, 980013)";

    @Autowired private PostCommandService postCommandService;
    @Autowired private ApplicationCommandService applicationCommandService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private long contestId;
    private long postId;

    @BeforeEach
    void setUp() {
        for (long memberId : List.of(AUTHOR_ID, APPLICANT_ID, INVITEE_ID, OTHER_INVITEE_ID)) {
            jdbcTemplate.update(
                    "INSERT INTO member (id, nickname, status) VALUES (?, ?, 'ACTIVE')",
                    memberId,
                    "t" + memberId);
        }
        jdbcTemplate.update(
                "INSERT INTO contest (source_url, source_site, title, category_id, end_date)"
                        + " VALUES ('https://test.local/invite-cancel', 'WEVITY', '초대 취소 공모전', 1,"
                        + " '2099-12-31')");
        contestId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        // 목표 2명(작성자 포함): 지원 하나를 수락하면 정원이 찬다
        jdbcTemplate.update(
                "INSERT INTO recruit_post (author_id, contest_id, title, content, target_count,"
                        + " recruit_start_date, recruit_end_date)"
                        + " VALUES (?, ?, '초대 취소 테스트 모집글', '내용', 2, '2026-11-01', '2099-12-01')",
                AUTHOR_ID,
                contestId);
        postId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbcTemplate.update(
                "INSERT INTO application (post_id, applicant_id) VALUES (?, ?)",
                postId,
                APPLICANT_ID);
        // 대기 초대 하나와 이미 거절된 초대 하나
        jdbcTemplate.update(
                "INSERT INTO team_invitation (post_id, inviter_id, invitee_id) VALUES (?, ?, ?)",
                postId,
                AUTHOR_ID,
                INVITEE_ID);
        jdbcTemplate.update(
                "INSERT INTO team_invitation (post_id, inviter_id, invitee_id, status, responded_at)"
                        + " VALUES (?, ?, ?, 'DECLINED', NOW())",
                postId,
                AUTHOR_ID,
                OTHER_INVITEE_ID);
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
        jdbcTemplate.update("DELETE FROM team_invitation WHERE post_id = ?", postId);
        jdbcTemplate.update("DELETE FROM application WHERE post_id = ?", postId);
        jdbcTemplate.update("DELETE FROM recruit_post WHERE id = ?", postId);
        jdbcTemplate.update("DELETE FROM contest WHERE id = ?", contestId);
        jdbcTemplate.update("DELETE FROM member WHERE id IN " + MEMBERS);
    }

    private String invitationStatus(long inviteeId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM team_invitation WHERE post_id = ? AND invitee_id = ?",
                String.class,
                postId,
                inviteeId);
    }

    @Test
    @DisplayName("모집글을 지우면 대기 초대는 CANCELED, 이미 거절된 초대는 그대로 (PST-004 ④)")
    void 삭제하면_대기_초대를_취소한다() {
        postCommandService.delete(AUTHOR_ID, postId);

        assertThat(invitationStatus(INVITEE_ID)).isEqualTo("CANCELED");
        assertThat(invitationStatus(OTHER_INVITEE_ID)).isEqualTo("DECLINED");
    }

    @Test
    @DisplayName("수동 모집 완료하면 대기 초대를 취소한다 (REC-006, INT-005 ④)")
    void 수동_모집_완료하면_대기_초대를_취소한다() {
        postCommandService.complete(AUTHOR_ID, postId);

        assertThat(invitationStatus(INVITEE_ID)).isEqualTo("CANCELED");
        assertThat(invitationStatus(OTHER_INVITEE_ID)).isEqualTo("DECLINED");
    }

    @Test
    @DisplayName("지원 수락으로 정원이 차 자동 모집완료되면 대기 초대를 취소한다 (REC-004 ⑤, INT-005 ④)")
    void 정원이_차면_대기_초대를_취소한다() {
        long applicationId =
                jdbcTemplate.queryForObject(
                        "SELECT id FROM application WHERE post_id = ?", Long.class, postId);

        applicationCommandService.accept(AUTHOR_ID, applicationId);

        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT status FROM recruit_post WHERE id = ?",
                                String.class,
                                postId))
                .isEqualTo("COMPLETED");
        assertThat(invitationStatus(INVITEE_ID)).isEqualTo("CANCELED");
    }

    @Test
    @DisplayName("팀 삭제로 글을 닫으면 모집완료·대기 지원 거절·대기 초대 취소, 다시 불러도 그대로 (WS-08)")
    void 팀_삭제로_닫으면_모집완료되고_초대를_취소한다() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 26, 20, 0);

        postCommandService.closeForTeamDeletion(postId, now);
        postCommandService.closeForTeamDeletion(postId, now.plusHours(1));

        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT status FROM recruit_post WHERE id = ?",
                                String.class,
                                postId))
                .isEqualTo("COMPLETED");
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT completed_at FROM recruit_post WHERE id = ?",
                                LocalDateTime.class,
                                postId))
                .isEqualTo(now);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT status FROM application WHERE post_id = ?",
                                String.class,
                                postId))
                .isEqualTo("REJECTED");
        assertThat(invitationStatus(INVITEE_ID)).isEqualTo("CANCELED");
        assertThat(invitationStatus(OTHER_INVITEE_ID)).isEqualTo("DECLINED");
    }
}
