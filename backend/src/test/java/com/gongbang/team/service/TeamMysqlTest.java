package com.gongbang.team.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 실제 MySQL에서 쿼리 의미와 행 잠금을 확인한다. Mockito로는 확인할 수 없는 부분만 둔다.
 *
 * <p>환경 변수 TEST_DATASOURCE_URL이 있을 때만 돈다. 반드시 테스트 전용 스키마(예: gongbang_test)를 가리키게 한다. Flyway가 V1부터
 * 적용하고, 테스트가 끝날 때마다 team_member·team 행을 모두 지운다.
 *
 * <p>개발용 DB를 지우지 않도록 스키마 이름에 _test가 없으면 건너뜀 (TEST_DATASOURCE_URL에 _test가 있어야 돈다).
 *
 * <pre>
 * TEST_DATASOURCE_URL=jdbc:mysql://localhost:3306/gongbang_test?serverTimezone=Asia/Seoul
 * TEST_DATASOURCE_USERNAME=root   (없으면 root)
 * TEST_DATASOURCE_PASSWORD=...    (없으면 빈 값)
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
class TeamMysqlTest {

    private static final Long LEADER_ID = 1L;

    @Autowired private TeamService teamService;

    @Autowired private TeamMemberService teamMemberService;

    @Autowired private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM team_member");
        jdbcTemplate.update("DELETE FROM team");
    }

    private void leave(Long teamId, Long memberId, String leaveType) {
        jdbcTemplate.update(
                "UPDATE team_member SET left_at = NOW(), leave_type = ?"
                        + " WHERE team_id = ? AND member_id = ? AND left_at IS NULL",
                leaveType,
                teamId,
                memberId);
    }

    @Test
    @DisplayName("같은 팀에 addMember가 동시에 들어와도 색 번호가 겹치지 않는다 (AGENTS.md 7)")
    void 같은_팀에_addMember가_동시에_들어와도_색_번호가_겹치지_않는다() throws Exception {
        Long teamId = teamService.createTeam(1L, "동시성팀", LEADER_ID);
        int threads = 7; // 팀장 1명 + 7명 = 8색 모두 사용
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < threads; i++) {
            long memberId = 100L + i;
            executor.submit(
                    () -> {
                        ready.countDown();
                        try {
                            start.await();
                            teamService.addMember(teamId, memberId);
                        } catch (Throwable t) {
                            errors.add(t);
                        } finally {
                            done.countDown();
                        }
                    });
        }
        ready.await();
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        assertThat(errors).isEmpty();
        List<Integer> colors =
                jdbcTemplate.queryForList(
                        "SELECT color_index FROM team_member WHERE team_id = ? AND left_at IS NULL"
                                + " ORDER BY color_index",
                        Integer.class,
                        teamId);
        assertThat(colors).containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
    }

    @Test
    @DisplayName("나간 팀원은 isActiveMember false, hasLeft true")
    void 나간_팀원은_isActiveMember_false_hasLeft_true() {
        Long teamId = teamService.createTeam(2L, "나가기팀", LEADER_ID);
        teamService.addMember(teamId, 200L);

        leave(teamId, 200L, "LEFT");

        assertThat(teamMemberService.isActiveMember(teamId, 200L)).isFalse();
        assertThat(teamMemberService.hasLeft(teamId, 200L)).isTrue();
    }

    @Test
    @DisplayName("강제 퇴장(KICKED)된 팀원도 hasLeft true (PST-006 ⑨)")
    void 강퇴된_팀원도_hasLeft_true() {
        Long teamId = teamService.createTeam(3L, "강퇴팀", LEADER_ID);
        teamService.addMember(teamId, 300L);

        leave(teamId, 300L, "KICKED");

        assertThat(teamMemberService.isActiveMember(teamId, 300L)).isFalse();
        assertThat(teamMemberService.hasLeft(teamId, 300L)).isTrue();
    }

    @Test
    @DisplayName("나갔다가 다시 들어온 팀원은 isActiveMember true, hasLeft true이고 행이 새로 생긴다")
    void 나갔다가_다시_들어온_팀원은_isActiveMember_true_hasLeft_true() {
        Long teamId = teamService.createTeam(4L, "재가입팀", LEADER_ID);
        teamService.addMember(teamId, 400L);
        leave(teamId, 400L, "LEFT");

        teamService.addMember(teamId, 400L);

        assertThat(teamMemberService.isActiveMember(teamId, 400L)).isTrue();
        assertThat(teamMemberService.hasLeft(teamId, 400L)).isTrue();
        List<Map<String, Object>> rows =
                jdbcTemplate.queryForList(
                        "SELECT left_at FROM team_member WHERE team_id = ? AND member_id = ?",
                        teamId,
                        400L);
        assertThat(rows).hasSize(2);
    }
}
