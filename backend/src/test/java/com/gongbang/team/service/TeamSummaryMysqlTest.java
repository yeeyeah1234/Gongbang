package com.gongbang.team.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.gongbang.team.dto.WorkspaceSummaryResponse;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
 * WS-02 워크스페이스 헤더 native 쿼리({@code TeamRepository#findWorkspaceSummary})를 실제 MySQL에서 확인한다.
 *
 * <p>TeamMysqlTest와 같은 방식: 환경 변수 TEST_DATASOURCE_URL(이름에 _test)이 있을 때만 돈다. 이 테스트가 넣은 행만 지운다.
 *
 * <pre>
 * TEST_DATASOURCE_URL=jdbc:mysql://127.0.0.1:3307/gongbang_test?serverTimezone=Asia/Seoul
 * </pre>
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
class TeamSummaryMysqlTest {

    private static final long LEADER_ID = 930_001L;
    private static final long MEMBER_ID = 930_002L;
    // recruit_post에 없는 번호 (team.post_id는 FK가 없어 넣을 수 있다)
    private static final long MISSING_POST_ID = 8_930_000_001L;

    @Autowired private TeamService teamService;
    @Autowired private TeamQueryService teamQueryService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private final List<Long> contestIds = new ArrayList<>();
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
        for (Long contestId : contestIds) {
            jdbcTemplate.update("DELETE FROM contest WHERE id = ?", contestId);
        }
    }

    private long insertAndGetId(String sql, Object... args) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(
                connection -> {
                    PreparedStatement ps =
                            connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                    for (int i = 0; i < args.length; i++) {
                        ps.setObject(i + 1, args[i]);
                    }
                    return ps;
                },
                keyHolder);
        return keyHolder.getKey().longValue();
    }

    private long contest(String title, LocalDate endDate) {
        long id =
                insertAndGetId(
                        "INSERT INTO contest (source_url, source_site, title, category_id, end_date)"
                                + " VALUES (?, 'WEVITY', ?,"
                                + " (SELECT id FROM category WHERE code = 'PLANNING'), ?)",
                        "https://test.local/team-summary/" + UUID.randomUUID(),
                        title,
                        endDate);
        contestIds.add(id);
        return id;
    }

    private long servicePost(long contestId) {
        long id =
                insertAndGetId(
                        "INSERT INTO recruit_post (author_id, contest_id, title, content,"
                                + " target_count, recruit_start_date, recruit_end_date)"
                                + " VALUES (?, ?, '요약 테스트 모집글', '내용', 3, '2026-11-01',"
                                + " '2099-12-01')",
                        LEADER_ID,
                        contestId);
        postIds.add(id);
        return id;
    }

    private long externalPost(String title, LocalDate extDeadline) {
        long id =
                insertAndGetId(
                        "INSERT INTO recruit_post (author_id, ext_contest_title, ext_category_id,"
                                + " ext_deadline, title, content, target_count,"
                                + " recruit_start_date, recruit_end_date)"
                                + " VALUES (?, ?, (SELECT id FROM category WHERE code ="
                                + " 'PLANNING'), ?, '외부 모집글', '내용', 3, '2026-11-01',"
                                + " '2099-12-01')",
                        LEADER_ID,
                        title,
                        extDeadline);
        postIds.add(id);
        return id;
    }

    private long team(long postId, String name) {
        long id = teamService.createTeam(postId, name, LEADER_ID);
        teamIds.add(id);
        return id;
    }

    @Test
    @DisplayName("서비스 공모전 팀: contest.title·end_date, 팀장은 isLeader true")
    void 서비스_공모전_팀은_contest_제목과_마감일() {
        long contestId = contest("공공데이터 공모전", LocalDate.of(2026, 12, 1));
        long teamId = team(servicePost(contestId), "공방팀");

        WorkspaceSummaryResponse response = teamQueryService.getWorkspaceSummary(teamId, LEADER_ID);

        assertThat(response)
                .isEqualTo(
                        new WorkspaceSummaryResponse(
                                teamId,
                                "공방팀",
                                contestId,
                                "공공데이터 공모전",
                                LocalDate.of(2026, 12, 1),
                                true,
                                LEADER_ID));
    }

    @Test
    @DisplayName("외부 공모전 팀: ext_contest_title·ext_deadline, 팀원은 isLeader false")
    void 외부_공모전_팀은_ext_제목과_마감일() {
        long teamId = team(externalPost("외부 공모전", LocalDate.of(2026, 12, 5)), "외부팀");
        teamService.addMember(teamId, MEMBER_ID);

        WorkspaceSummaryResponse response = teamQueryService.getWorkspaceSummary(teamId, MEMBER_ID);

        assertThat(response.contestId()).isNull();
        assertThat(response.contestTitle()).isEqualTo("외부 공모전");
        assertThat(response.deadline()).isEqualTo(LocalDate.of(2026, 12, 5));
        assertThat(response.isLeader()).isFalse();
    }

    @Test
    @DisplayName("모집글이 삭제돼도 공모전명·마감일을 읽는다 (PST-004 ④)")
    void 삭제된_모집글도_읽는다() {
        long postId = externalPost("삭제 공모전", LocalDate.of(2026, 12, 9));
        long teamId = team(postId, "삭제팀");
        jdbcTemplate.update("UPDATE recruit_post SET deleted_at = NOW() WHERE id = ?", postId);

        WorkspaceSummaryResponse response = teamQueryService.getWorkspaceSummary(teamId, LEADER_ID);

        assertThat(response.contestTitle()).isEqualTo("삭제 공모전");
        assertThat(response.deadline()).isEqualTo(LocalDate.of(2026, 12, 9));
    }

    @Test
    @DisplayName("모집글 행이 없으면 팀 이름만 있고 공모전명·마감일은 null")
    void 모집글이_없으면_팀_이름만() {
        long teamId = team(MISSING_POST_ID, "고아팀");

        WorkspaceSummaryResponse response = teamQueryService.getWorkspaceSummary(teamId, LEADER_ID);

        assertThat(response.teamName()).isEqualTo("고아팀");
        assertThat(response.contestId()).isNull();
        assertThat(response.contestTitle()).isNull();
        assertThat(response.deadline()).isNull();
    }
}
