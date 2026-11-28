package com.gongbang.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.gongbang.chat.service.ChatMembershipService;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.notification.service.EmailOutboxService;
import com.gongbang.team.service.TeamMemberManagementService;
import com.gongbang.team.service.TeamService;
import com.gongbang.workspace.sync.SyncServerClient;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(
        properties = {
            "spring.autoconfigure.exclude=",
            "spring.datasource.url=${TEST_DATASOURCE_URL:}",
            "spring.datasource.username=${TEST_DATASOURCE_USERNAME:root}",
            "spring.datasource.password=${TEST_DATASOURCE_PASSWORD:}",
            "spring.jpa.properties.hibernate.show_sql=false"
        })
@EnabledIfEnvironmentVariable(named = "TEST_DATASOURCE_URL", matches = ".*_test.*")
class AccountWithdrawalMysqlTest {
    @Autowired AccountService accounts;
    @Autowired com.gongbang.invitation.service.InvitationCommandService invitations;
    @Autowired TeamService teamService;
    @Autowired TeamMemberManagementService management;
    @Autowired ChatMembershipService chatMembershipService;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired PlatformTransactionManager transactions;
    @MockBean EmailOutboxService mail;
    @MockBean SyncServerClient sync;
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
            jdbcTemplate.update("DELETE FROM team_invitation WHERE post_id = ?", postId);
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
                                    "INSERT INTO member (email, nickname, login_type) VALUES (?, ?, 'GOOGLE')",
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

    private void withdraw(long member) {
        accounts.withdraw(member, null, "탈퇴합니다");
    }

    private String status(long member) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM member WHERE id=?", String.class, member);
    }

    private int count(long post) {
        return jdbcTemplate.queryForObject(
                "SELECT current_count FROM recruit_post WHERE id=?", Integer.class, post);
    }

    private long membership(long member, long leader, int count) {
        long post = createPost(leader, 4, count, "COMPLETED");
        long team = createTeamWithPost(post, "탈퇴검증", leader);
        teamService.addMember(team, member);
        chatMembershipService.createTeamRoom(team, leader);
        chatMembershipService.addMemberToTeamRoom(team, member);
        return team;
    }

    @Test
    void 팀장이면_다른팀에서도_나가지_않는다() {
        long member = createMember(), leader = createMember();
        membership(member, leader, 2);
        createTeamWithPost(createPost(member, 3, 1, "RECRUITING"), "팀장", member);
        assertThat(assertThrows(BusinessException.class, () -> withdraw(member)).getErrorCode())
                .isEqualTo(ErrorCode.LEADER_CANNOT_WITHDRAW);
        assertThat(status(member)).isEqualTo("ACTIVE");
        assertThat(count(postIds.get(0))).isEqualTo(2);
        verifyNoInteractions(mail, sync);
    }

    @Test
    void 여러팀과_삭제된모집글에서도_탈퇴와_채팅제거를_함께_처리한다() {
        long member = createMember();
        membership(member, createMember(), 2);
        membership(member, createMember(), 2);
        jdbcTemplate.update("UPDATE recruit_post SET deleted_at=NOW() WHERE id=?", postIds.get(0));
        withdraw(member);
        assertThat(status(member)).isEqualTo("WITHDRAWN");
        for (long post : postIds) {
            assertThat(count(post)).isEqualTo(1);
            assertThat(
                            jdbcTemplate.queryForObject(
                                    "SELECT status FROM recruit_post WHERE id=?",
                                    String.class,
                                    post))
                    .isEqualTo("COMPLETED");
        }
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM team_member WHERE member_id=? AND left_at IS NULL",
                                Integer.class,
                                member))
                .isZero();
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM chat_room_member WHERE member_id=?",
                                Integer.class,
                                member))
                .isZero();
        verify(mail).enqueueWithdrawn(any());
        for (long team : teamIds) verify(sync).disconnect(team, member);
    }

    @Test
    void 뒤쪽팀_처리실패면_앞쪽팀도_롤백한다() {
        long member = createMember();
        membership(member, createMember(), 2);
        membership(member, createMember(), 1);
        assertThrows(IllegalStateException.class, () -> withdraw(member));
        assertThat(status(member)).isEqualTo("ACTIVE");
        assertThat(count(postIds.get(0))).isEqualTo(2);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM team_member WHERE member_id=? AND left_at IS NULL",
                                Integer.class,
                                member))
                .isEqualTo(2);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM chat_room_member WHERE member_id=?",
                                Integer.class,
                                member))
                .isEqualTo(2);
        verifyNoInteractions(mail, sync);
    }

    @Test
    void 중복탈퇴는_인원을_한번만_줄인다() throws Exception {
        long member = createMember();
        membership(member, createMember(), 2);
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        Callable<Boolean> action =
                () -> {
                    start.await();
                    try {
                        withdraw(member);
                        return true;
                    } catch (BusinessException e) {
                        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
                        return false;
                    }
                };
        try {
            var a = pool.submit(action);
            var b = pool.submit(action);
            start.countDown();
            assertThat(List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        } finally {
            pool.shutdownNow();
        }
        assertThat(count(postIds.get(0))).isEqualTo(1);
        verify(mail).enqueueWithdrawn(any());
    }

    private TransactionTemplate transaction() {
        var tx = new TransactionTemplate(transactions);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return tx;
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new AssertionError("latch timeout");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    @Test
    void 탈퇴커밋을_기다린_팀가입은_거절한다() throws Exception {
        long member = createMember(), leader = createMember();
        long team = createTeamWithPost(createPost(leader, 3, 1, "RECRUITING"), "가입경합", leader);
        long post = postIds.get(0);
        jdbcTemplate.update(
                "INSERT INTO team_invitation(post_id,inviter_id,invitee_id) VALUES (?,?,?)",
                post,
                leader,
                member);
        long invitation =
                jdbcTemplate.queryForObject(
                        "SELECT id FROM team_invitation WHERE post_id=? AND invitee_id=?",
                        Long.class,
                        post,
                        member);
        var pool = Executors.newFixedThreadPool(2);
        var locked = new CountDownLatch(1);
        var commit = new CountDownLatch(1);
        var started = new CountDownLatch(1);
        try {
            var withdrawal =
                    pool.submit(
                            () ->
                                    transaction()
                                            .executeWithoutResult(
                                                    s -> {
                                                        withdraw(member);
                                                        locked.countDown();
                                                        await(commit);
                                                    }));
            await(locked);
            var join =
                    pool.submit(
                            () -> {
                                started.countDown();
                                return assertThrows(
                                        BusinessException.class,
                                        () -> invitations.accept(invitation, member));
                            });
            await(started);
            assertThrows(TimeoutException.class, () -> join.get(250, TimeUnit.MILLISECONDS));
            commit.countDown();
            withdrawal.get(15, TimeUnit.SECONDS);
            assertThat(join.get(15, TimeUnit.SECONDS).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_INPUT);
            assertThat(
                            jdbcTemplate.queryForObject(
                                    "SELECT COUNT(*) FROM team_member WHERE member_id=?",
                                    Integer.class,
                                    member))
                    .isZero();
            assertThat(count(post)).isEqualTo(1);
            assertThat(
                            jdbcTemplate.queryForObject(
                                    "SELECT status FROM team_invitation WHERE id=?",
                                    String.class,
                                    invitation))
                    .isEqualTo("PENDING");
        } finally {
            commit.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void 위임커밋을_기다린_탈퇴는_새팀장_역할을_다시_확인한다() throws Exception {
        long member = createMember(), leader = createMember();
        long team = membership(member, leader, 2);
        var pool = Executors.newFixedThreadPool(2);
        var locked = new CountDownLatch(1);
        var commit = new CountDownLatch(1);
        var started = new CountDownLatch(1);
        try {
            var delegation =
                    pool.submit(
                            () ->
                                    transaction()
                                            .executeWithoutResult(
                                                    s -> {
                                                        management.delegateLeader(
                                                                team, leader, member);
                                                        locked.countDown();
                                                        await(commit);
                                                    }));
            await(locked);
            var withdrawal =
                    pool.submit(
                            () -> {
                                started.countDown();
                                return assertThrows(
                                        BusinessException.class, () -> withdraw(member));
                            });
            await(started);
            assertThrows(TimeoutException.class, () -> withdrawal.get(250, TimeUnit.MILLISECONDS));
            commit.countDown();
            delegation.get(15, TimeUnit.SECONDS);
            assertThat(withdrawal.get(15, TimeUnit.SECONDS).getErrorCode())
                    .isEqualTo(ErrorCode.LEADER_CANNOT_WITHDRAW);
            assertThat(status(member)).isEqualTo("ACTIVE");
            assertThat(count(postIds.get(0))).isEqualTo(2);
        } finally {
            commit.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void 탈퇴대기중_새팀가입이_끝나면_탈퇴를_재시도하도록_되돌린다() throws Exception {
        long member = createMember();
        membership(member, createMember(), 2);
        long post = postIds.get(0), leader = createMember();
        long nextPost = createPost(leader, 4, 1, "RECRUITING");
        long nextTeam = createTeamWithPost(nextPost, "새참여팀", leader);
        chatMembershipService.createTeamRoom(nextTeam, leader);
        jdbcTemplate.update(
                "INSERT INTO team_invitation(post_id,inviter_id,invitee_id) VALUES (?,?,?)",
                nextPost,
                leader,
                member);
        long invitation =
                jdbcTemplate.queryForObject(
                        "SELECT id FROM team_invitation WHERE post_id=? AND invitee_id=?",
                        Long.class,
                        nextPost,
                        member);
        var pool = Executors.newFixedThreadPool(2);
        var locked = new CountDownLatch(1);
        var commit = new CountDownLatch(1);
        var started = new CountDownLatch(1);
        try {
            var blocker =
                    pool.submit(
                            () ->
                                    transaction()
                                            .executeWithoutResult(
                                                    s -> {
                                                        jdbcTemplate.queryForObject(
                                                                "SELECT id FROM recruit_post WHERE id=? FOR UPDATE",
                                                                Long.class,
                                                                post);
                                                        locked.countDown();
                                                        await(commit);
                                                    }));
            await(locked);
            var withdrawal =
                    pool.submit(
                            () -> {
                                started.countDown();
                                return assertThrows(
                                        BusinessException.class, () -> withdraw(member));
                            });
            await(started);
            assertThrows(TimeoutException.class, () -> withdrawal.get(250, TimeUnit.MILLISECONDS));
            invitations.accept(invitation, member);
            commit.countDown();
            blocker.get(15, TimeUnit.SECONDS);
            assertThat(withdrawal.get(15, TimeUnit.SECONDS).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_INPUT);
            assertThat(status(member)).isEqualTo("ACTIVE");
            assertThat(count(post)).isEqualTo(2);
            assertThat(count(nextPost)).isEqualTo(2);
            assertThat(
                            jdbcTemplate.queryForObject(
                                    "SELECT COUNT(*) FROM team_member WHERE member_id=? AND left_at IS NULL",
                                    Integer.class,
                                    member))
                    .isEqualTo(2);
            verifyNoInteractions(mail, sync);
        } finally {
            commit.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void 삭제된팀의_남은팀장행은_탈퇴를_막지않는다() {
        long member = createMember();
        long team = createTeamWithPost(createPost(member, 3, 1, "RECRUITING"), "삭제팀", member);
        chatMembershipService.createTeamRoom(team, member);
        jdbcTemplate.update("UPDATE team SET deleted_at=NOW() WHERE id=?", team);
        withdraw(member);
        assertThat(status(member)).isEqualTo("WITHDRAWN");
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM chat_room_member WHERE member_id=?",
                                Integer.class,
                                member))
                .isZero();
    }
}
