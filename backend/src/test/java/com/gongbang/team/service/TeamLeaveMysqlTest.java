package com.gongbang.team.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.gongbang.chat.service.ChatMembershipService;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
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
 * WS-09 팀 나가기를 실제 MySQL에서 확인한다(잠금 순서·동시 이중 요청·확정 인원 감소·모집완료 유지).
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
class TeamLeaveMysqlTest {

    @Autowired private TeamService teamService;
    @Autowired private TeamMemberService teamMemberService;
    @Autowired private TeamLeaveService teamLeaveService;
    @Autowired private ChatMembershipService chatMembershipService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private final List<Long> memberIds = new ArrayList<>();
    private final List<Long> teamIds = new ArrayList<>();
    private final List<Long> postIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
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
                    ps.setString(1, "leave-" + suffix + "@test.local");
                    ps.setString(2, "lv" + suffix);
                    return ps;
                },
                keyHolder);
        long id = keyHolder.getKey().longValue();
        memberIds.add(id);
        return id;
    }

    private long createPost(long authorId, int targetCount, int currentCount, String status) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(
                connection -> {
                    PreparedStatement ps =
                            connection.prepareStatement(
                                    "INSERT INTO recruit_post (author_id, ext_contest_title,"
                                            + " ext_category_id, ext_deadline, title, content,"
                                            + " target_count, current_count, status,"
                                            + " recruit_start_date, recruit_end_date)"
                                            + " VALUES (?, '나가기 공모전', (SELECT id FROM category"
                                            + " WHERE code = 'PLANNING'), ?, '팀 나가기 테스트 글', '내용',"
                                            + " ?, ?, ?, '2026-11-01', '2099-12-01')",
                                    Statement.RETURN_GENERATED_KEYS);
                    ps.setLong(1, authorId);
                    ps.setObject(2, LocalDate.of(2099, 12, 10));
                    ps.setInt(3, targetCount);
                    ps.setInt(4, currentCount);
                    ps.setString(5, status);
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
    @DisplayName("팀에서 나가면 isActiveMember false, leave_type LEFT, 확정 인원 -1, 모집완료 상태 유지")
    void 팀_나가기_상태와_인원감소_모집완료_유지() {
        long leaderId = createMember();
        long memberAId = createMember();
        long postId = createPost(leaderId, 2, 2, "COMPLETED");
        long teamId = createTeamWithPost(postId, "완료된팀", leaderId);
        teamService.addMember(teamId, memberAId);
        chatMembershipService.createTeamRoom(teamId, leaderId);
        chatMembershipService.addMemberToTeamRoom(teamId, memberAId);

        teamLeaveService.leaveTeam(teamId, memberAId);

        assertThat(teamMemberService.isActiveMember(teamId, memberAId)).isFalse();
        assertThat(teamMemberService.hasLeft(teamId, memberAId)).isTrue();
        String leaveType =
                jdbcTemplate.queryForObject(
                        "SELECT leave_type FROM team_member WHERE team_id = ? AND member_id = ?",
                        String.class,
                        teamId,
                        memberAId);
        assertThat(leaveType).isEqualTo("LEFT");

        Integer currentCount =
                jdbcTemplate.queryForObject(
                        "SELECT current_count FROM recruit_post WHERE id = ?",
                        Integer.class,
                        postId);
        String status =
                jdbcTemplate.queryForObject(
                        "SELECT status FROM recruit_post WHERE id = ?", String.class, postId);
        assertThat(currentCount).isEqualTo(1);
        assertThat(status).isEqualTo("COMPLETED");

        Integer chatRoomMembers =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM chat_room_member crm"
                                + " JOIN chat_room cr ON cr.id = crm.chat_room_id"
                                + " WHERE cr.team_id = ? AND crm.member_id = ?",
                        Integer.class,
                        teamId,
                        memberAId);
        assertThat(chatRoomMembers).isZero();
    }

    @Test
    @DisplayName("팀장은 나갈 수 없고 409 LEADER_CANNOT_LEAVE이며 인원도 변하지 않는다")
    void 팀장_나가기_거절() {
        long leaderId = createMember();
        long memberAId = createMember();
        long postId = createPost(leaderId, 3, 2, "RECRUITING");
        long teamId = createTeamWithPost(postId, "팀장팀", leaderId);
        teamService.addMember(teamId, memberAId);

        BusinessException ex =
                assertThrows(
                        BusinessException.class,
                        () -> teamLeaveService.leaveTeam(teamId, leaderId));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.LEADER_CANNOT_LEAVE);
        assertThat(teamMemberService.isActiveMember(teamId, leaderId)).isTrue();
        Integer currentCount =
                jdbcTemplate.queryForObject(
                        "SELECT current_count FROM recruit_post WHERE id = ?",
                        Integer.class,
                        postId);
        assertThat(currentCount).isEqualTo(2);
    }

    @Test
    @DisplayName("3명 팀에서 같은 팀원이 동시에 두 번 나가기를 요청해도 한 번만 처리되고 인원이 1만 줄어든다")
    void 동시_두번_나가기_3명팀_한번만_처리() throws Exception {
        long leaderId = createMember();
        long memberAId = createMember();
        long memberBId = createMember();
        long postId = createPost(leaderId, 4, 3, "RECRUITING");
        long teamId = createTeamWithPost(postId, "동시나가기3명", leaderId);
        teamService.addMember(teamId, memberAId);
        teamService.addMember(teamId, memberBId);

        ConcurrentLeaveResult result = runConcurrentDoubleLeave(teamId, memberAId);

        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.notMemberErrors()).isEqualTo(1);
        assertThat(result.unexpectedErrors()).isEmpty();
        assertThat(teamMemberService.isActiveMember(teamId, memberAId)).isFalse();
        Integer currentCount =
                jdbcTemplate.queryForObject(
                        "SELECT current_count FROM recruit_post WHERE id = ?",
                        Integer.class,
                        postId);
        assertThat(currentCount).isEqualTo(2);
    }

    @Test
    @DisplayName("2명 팀에서 같은 팀원이 동시에 두 번 나가기를 요청해도 한 번만 성공하고 나머지는 403 NOT_TEAM_MEMBER다")
    void 동시_두번_나가기_2명팀_한번만_처리() throws Exception {
        long leaderId = createMember();
        long memberAId = createMember();
        long postId = createPost(leaderId, 3, 2, "RECRUITING");
        long teamId = createTeamWithPost(postId, "동시나가기2명", leaderId);
        teamService.addMember(teamId, memberAId);

        ConcurrentLeaveResult result = runConcurrentDoubleLeave(teamId, memberAId);

        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.notMemberErrors()).isEqualTo(1);
        assertThat(result.unexpectedErrors()).isEmpty();
        assertThat(teamMemberService.isActiveMember(teamId, memberAId)).isFalse();
        Integer currentCount =
                jdbcTemplate.queryForObject(
                        "SELECT current_count FROM recruit_post WHERE id = ?",
                        Integer.class,
                        postId);
        assertThat(currentCount).isEqualTo(1);
    }

    private ConcurrentLeaveResult runConcurrentDoubleLeave(long teamId, long memberId)
            throws Exception {
        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger notMember = new AtomicInteger();
        ConcurrentLinkedQueue<Throwable> unexpected = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < threads; i++) {
            executor.submit(
                    () -> {
                        ready.countDown();
                        try {
                            start.await();
                            teamLeaveService.leaveTeam(teamId, memberId);
                            success.incrementAndGet();
                        } catch (BusinessException ex) {
                            if (ex.getErrorCode() == ErrorCode.NOT_TEAM_MEMBER) {
                                notMember.incrementAndGet();
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
        return new ConcurrentLeaveResult(success.get(), notMember.get(), List.copyOf(unexpected));
    }

    private record ConcurrentLeaveResult(
            int successCount, int notMemberErrors, List<Throwable> unexpectedErrors) {}
}
