package com.gongbang.team.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.gongbang.team.dto.MyTeamResponse;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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
 * WS-08 팀 목록 native 쿼리({@code TeamRepository#findMyTeams})를 실제 MySQL에서 확인한다.
 *
 * <p>TeamSummaryMysqlTest와 같은 방식: 환경 변수 TEST_DATASOURCE_URL(이름에 _test)이 있을 때만 돈다. 이 테스트가 넣은 행만 지운다.
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
class TeamListMysqlTest {

    private static final long LEADER_ID = 940_001L;
    private static final long MEMBER_ID = 940_002L;

    @Autowired private TeamService teamService;
    @Autowired private TeamListService teamListService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private final List<Long> postIds = new ArrayList<>();
    private final List<Long> teamIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (Long teamId : teamIds) {
            jdbcTemplate.update("DELETE FROM team_member WHERE team_id = ?", teamId);
            jdbcTemplate.update("DELETE FROM team WHERE id = ?", teamId);
        }
        for (Long postId : postIds) {
            jdbcTemplate.update("DELETE FROM recruit_post WHERE id = ?", postId);
        }
    }

    private long externalPost(String title, LocalDate extDeadline) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(
                connection -> {
                    PreparedStatement ps =
                            connection.prepareStatement(
                                    "INSERT INTO recruit_post (author_id, ext_contest_title,"
                                            + " ext_category_id, ext_deadline, title, content,"
                                            + " target_count, recruit_start_date, recruit_end_date)"
                                            + " VALUES (?, ?, (SELECT id FROM category WHERE code ="
                                            + " 'PLANNING'), ?, '목록 테스트 모집글', '내용', 3,"
                                            + " '2026-11-01', '2099-12-01')",
                                    Statement.RETURN_GENERATED_KEYS);
                    ps.setLong(1, LEADER_ID);
                    ps.setString(2, title);
                    ps.setObject(3, extDeadline);
                    return ps;
                },
                keyHolder);
        long id = keyHolder.getKey().longValue();
        postIds.add(id);
        return id;
    }

    private long team(String contestTitle, LocalDate deadline, String name) {
        long id = teamService.createTeam(externalPost(contestTitle, deadline), name, LEADER_ID);
        teamIds.add(id);
        return id;
    }

    @Test
    @DisplayName("현재 팀원인 팀만, 공모전명·마감일·팀장 여부·팀원을 채운다")
    void 현재_팀원인_팀만() {
        long joined = team("외부 공모전", LocalDate.of(2099, 12, 5), "같이팀");
        teamService.addMember(joined, MEMBER_ID);
        long left = team("나간 공모전", LocalDate.of(2099, 12, 6), "나간팀");
        teamService.addMember(left, MEMBER_ID);
        jdbcTemplate.update(
                "UPDATE team_member SET left_at = NOW(), leave_type = 'LEFT'"
                        + " WHERE team_id = ? AND member_id = ?",
                left,
                MEMBER_ID);

        List<MyTeamResponse> mine = teamListService.getMyTeams(MEMBER_ID);

        assertThat(mine).extracting(MyTeamResponse::teamId).containsExactly(joined);
        MyTeamResponse card = mine.get(0);
        assertThat(card.name()).isEqualTo("같이팀");
        assertThat(card.contestTitle()).isEqualTo("외부 공모전");
        assertThat(card.deadline()).isEqualTo(LocalDate.of(2099, 12, 5));
        assertThat(card.isEnded()).isFalse();
        assertThat(card.isLeader()).isFalse();
        assertThat(card.memberCount()).isEqualTo(2);
        assertThat(card.members())
                .extracting(MyTeamResponse.Member::memberId)
                .containsExactly(LEADER_ID, MEMBER_ID);
        assertThat(card.lastActivityAt()).isNotNull();

        assertThat(teamListService.getMyTeams(LEADER_ID))
                .extracting(MyTeamResponse::isLeader)
                .containsOnly(true);
    }
}
