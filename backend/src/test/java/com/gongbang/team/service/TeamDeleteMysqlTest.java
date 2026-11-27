package com.gongbang.team.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.gongbang.chat.service.ChatMembershipService;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.team.dto.MyTeamResponse;
import com.gongbang.workspace.sync.TeamSyncService;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

/**
 * WS-08 ⑨ 팀 삭제(소프트 삭제)를 실제 MySQL에서 확인한다.
 *
 * <p>환경 변수 TEST_DATASOURCE_URL(스키마 이름에 _test 포함)이 있을 때만 실행되며, 테스트가 만든 행만 정리한다.
 */
@SpringBootTest(
        properties = {
            "spring.autoconfigure.exclude=",
            "spring.datasource.url=${TEST_DATASOURCE_URL:}",
            "spring.datasource.username=${TEST_DATASOURCE_USERNAME:root}",
            "spring.datasource.password=${TEST_DATASOURCE_PASSWORD:}",
            "spring.jpa.properties.hibernate.show_sql=false"
        })
@EnabledIfEnvironmentVariable(named = "TEST_DATASOURCE_URL", matches = ".*_test.*")
class TeamDeleteMysqlTest {

    @Autowired private TeamService teamService;
    @Autowired private TeamMemberService teamMemberService;
    @Autowired private TeamListService teamListService;
    @Autowired private TeamDeleteService teamDeleteService;
    @Autowired private TeamSyncService teamSyncService;
    @Autowired private ChatMembershipService chatMembershipService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private final List<Long> memberIds = new ArrayList<>();
    private final List<Long> teamIds = new ArrayList<>();
    private final List<Long> postIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (Long memberId : memberIds) {
            jdbcTemplate.update("DELETE FROM email_outbox WHERE receiver_id = ?", memberId);
            jdbcTemplate.update("DELETE FROM notification WHERE receiver_id = ?", memberId);
        }
        for (Long teamId : teamIds) {
            String teamRooms = "SELECT id FROM chat_room WHERE team_id = ?";
            jdbcTemplate.update(
                    "DELETE FROM chat_message WHERE chat_room_id IN (" + teamRooms + ")", teamId);
            jdbcTemplate.update(
                    "DELETE FROM chat_room_member WHERE chat_room_id IN (" + teamRooms + ")",
                    teamId);
            jdbcTemplate.update("DELETE FROM chat_room WHERE team_id = ?", teamId);
            jdbcTemplate.update("DELETE FROM team_member WHERE team_id = ?", teamId);
            jdbcTemplate.update("DELETE FROM team WHERE id = ?", teamId);
        }
        for (Long postId : postIds) {
            jdbcTemplate.update("DELETE FROM team_invitation WHERE post_id = ?", postId);
            jdbcTemplate.update("DELETE FROM application WHERE post_id = ?", postId);
            jdbcTemplate.update("DELETE FROM recruit_post WHERE id = ?", postId);
        }
        for (Long memberId : memberIds) {
            jdbcTemplate.update("DELETE FROM member WHERE id = ?", memberId);
        }
    }

    private long createMember() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(
                connection -> {
                    PreparedStatement ps =
                            connection.prepareStatement(
                                    "INSERT INTO member (email, nickname) VALUES (?, ?)",
                                    Statement.RETURN_GENERATED_KEYS);
                    ps.setString(1, "delete-" + suffix + "@test.local");
                    ps.setString(2, "dl" + suffix);
                    return ps;
                },
                keyHolder);
        long id = keyHolder.getKey().longValue();
        memberIds.add(id);
        return id;
    }

    private long createPost(long authorId, int targetCount, int currentCount) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(
                connection -> {
                    PreparedStatement ps =
                            connection.prepareStatement(
                                    "INSERT INTO recruit_post (author_id, ext_contest_title,"
                                            + " ext_category_id, ext_deadline, title, content,"
                                            + " target_count, current_count, status,"
                                            + " recruit_start_date, recruit_end_date)"
                                            + " VALUES (?, '삭제 공모전', (SELECT id FROM category"
                                            + " WHERE code = 'PLANNING'), ?, '팀 삭제 테스트 글', '내용',"
                                            + " ?, ?, 'RECRUITING', '2026-11-01', '2099-12-01')",
                                    Statement.RETURN_GENERATED_KEYS);
                    ps.setLong(1, authorId);
                    ps.setObject(2, LocalDate.of(2099, 12, 10));
                    ps.setInt(3, targetCount);
                    ps.setInt(4, currentCount);
                    return ps;
                },
                keyHolder);
        long postId = keyHolder.getKey().longValue();
        postIds.add(postId);
        return postId;
    }

    private long createTeamWithPost(long postId, String name, long leaderId) {
        long teamId = teamService.createTeam(postId, name, leaderId);
        teamIds.add(teamId);
        return teamId;
    }

    @Test
    @DisplayName(
            "팀 삭제 후 isDeleted true, isActiveMember·isLeader false, 팀장 left_at 기록, 목록 제외, 내부 access 거부")
    void 팀_삭제_후_권한_목록_내부접근_차단() {
        long leaderId = createMember();
        long postId = createPost(leaderId, 3, 1);
        long teamId = createTeamWithPost(postId, "혼자남은팀", leaderId);
        chatMembershipService.createTeamRoom(teamId, leaderId);

        teamDeleteService.deleteTeam(teamId, leaderId);

        assertThat(teamService.isDeleted(teamId)).isTrue();
        assertThat(teamMemberService.isActiveMember(teamId, leaderId)).isFalse();
        assertThat(teamMemberService.isLeader(teamId, leaderId)).isFalse();

        Timestamp leaderLeftAt =
                jdbcTemplate.queryForObject(
                        "SELECT left_at FROM team_member WHERE team_id = ? AND member_id = ?",
                        Timestamp.class,
                        teamId,
                        leaderId);
        assertThat(leaderLeftAt).isNotNull();

        assertThat(teamListService.getMyTeams(leaderId))
                .extracting(MyTeamResponse::teamId)
                .doesNotContain(teamId);

        BusinessException accessEx =
                assertThrows(
                        BusinessException.class,
                        () -> teamSyncService.checkAccess(leaderId, teamId));
        assertThat(accessEx.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);

        BusinessException existsEx =
                assertThrows(BusinessException.class, () -> teamService.requireExists(teamId));
        assertThat(existsEx.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);

        BusinessException lockEx =
                assertThrows(BusinessException.class, () -> teamService.lockTeam(teamId));
        assertThat(lockEx.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);

        Integer chatRoomMembers =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM chat_room_member crm"
                                + " JOIN chat_room cr ON cr.id = crm.chat_room_id"
                                + " WHERE cr.team_id = ? AND crm.member_id = ?",
                        Integer.class,
                        teamId,
                        leaderId);
        assertThat(chatRoomMembers).isZero();
    }

    @Test
    @DisplayName("활성 팀원이 2명이면 409 TEAM_HAS_OTHER_MEMBERS로 거절하고 팀은 유지된다")
    void 팀원이_2명이면_409() {
        long leaderId = createMember();
        long memberId = createMember();
        long postId = createPost(leaderId, 3, 2);
        long teamId = createTeamWithPost(postId, "둘이있는팀", leaderId);
        teamService.addMember(teamId, memberId);

        BusinessException ex =
                assertThrows(
                        BusinessException.class,
                        () -> teamDeleteService.deleteTeam(teamId, leaderId));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.TEAM_HAS_OTHER_MEMBERS);
        assertThat(teamService.isDeleted(teamId)).isFalse();
        assertThat(teamMemberService.isLeader(teamId, leaderId)).isTrue();
    }

    @Test
    @DisplayName("같은 팀을 동시에 두 번 삭제하면 하나만 성공하고 나머지는 404 RESOURCE_NOT_FOUND다")
    void 같은_팀_동시_두번_삭제시_하나만_성공() throws Exception {
        long leaderId = createMember();
        long postId = createPost(leaderId, 3, 1);
        long teamId = createTeamWithPost(postId, "동시삭제팀", leaderId);
        chatMembershipService.createTeamRoom(teamId, leaderId);

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger notFound = new AtomicInteger();
        ConcurrentLinkedQueue<Throwable> unexpected = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < threads; i++) {
            executor.submit(
                    () -> {
                        ready.countDown();
                        try {
                            start.await();
                            teamDeleteService.deleteTeam(teamId, leaderId);
                            success.incrementAndGet();
                        } catch (BusinessException ex) {
                            if (ex.getErrorCode() == ErrorCode.RESOURCE_NOT_FOUND) {
                                notFound.incrementAndGet();
                            } else {
                                unexpected.add(ex);
                            }
                        } catch (Throwable t) {
                            unexpected.add(t);
                        } finally {
                            done.countDown();
                        }
                    });
        }
        ready.await();
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        assertThat(success.get()).isEqualTo(1);
        assertThat(notFound.get()).isEqualTo(1);
        assertThat(unexpected).isEmpty();
        assertThat(teamService.isDeleted(teamId)).isTrue();
        Timestamp leaderLeftAt =
                jdbcTemplate.queryForObject(
                        "SELECT left_at FROM team_member WHERE team_id = ? AND member_id = ?",
                        Timestamp.class,
                        teamId,
                        leaderId);
        assertThat(leaderLeftAt).isNotNull();
    }

    @Test
    @DisplayName("모집중 모집글이 있는 팀을 삭제하면 모집글은 모집완료, 대기 지원은 REJECTED가 된다")
    void 모집중_모집글이_있는_팀_삭제시_모집완료와_대기지원_거절() {
        long leaderId = createMember();
        long applicantId = createMember();
        long postId = createPost(leaderId, 3, 1);
        long teamId = createTeamWithPost(postId, "모집마감팀", leaderId);
        chatMembershipService.createTeamRoom(teamId, leaderId);
        jdbcTemplate.update(
                "INSERT INTO application (post_id, applicant_id) VALUES (?, ?)",
                postId,
                applicantId);

        teamDeleteService.deleteTeam(teamId, leaderId);

        assertThat(teamService.isDeleted(teamId)).isTrue();
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT status FROM recruit_post WHERE id = ?",
                                String.class,
                                postId))
                .isEqualTo("COMPLETED");
        LocalDateTime completedAt =
                jdbcTemplate.queryForObject(
                        "SELECT completed_at FROM recruit_post WHERE id = ?",
                        LocalDateTime.class,
                        postId);
        LocalDateTime teamDeletedAt =
                jdbcTemplate.queryForObject(
                        "SELECT deleted_at FROM team WHERE id = ?", LocalDateTime.class, teamId);
        assertThat(completedAt).isNotNull().isEqualTo(teamDeletedAt);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT status FROM application WHERE post_id = ?"
                                        + " AND applicant_id = ?",
                                String.class,
                                postId,
                                applicantId))
                .isEqualTo("REJECTED");
    }
}
