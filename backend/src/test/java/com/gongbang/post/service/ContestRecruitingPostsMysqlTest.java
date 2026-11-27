package com.gongbang.post.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.gongbang.global.common.PageResponse;
import com.gongbang.post.dto.PostListItemResponse;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * PST-001 ⑩ 공모전으로 거르기와 공모전 상세의 「팀원 모집 중」 칸(MAIN-02 ⑨)이 실제 MySQL에서 맞는 글만 최신순으로 고르는지 확인한다.
 *
 * <p>환경 변수 TEST_DATASOURCE_URL이 있을 때만 돈다 (MyRecruitingPostsMysqlTest와 같은 방식). 반드시 테스트 전용 스키마(이름에
 * _test)를 가리키게 한다. 테스트가 끝나면 이 테스트가 넣은 공모전·모집글 행을 지운다.
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
class ContestRecruitingPostsMysqlTest {

    // 다른 테스트 데이터와 겹치지 않게 큰 번호를 쓴다
    private static final long AUTHOR_ID = 990_001L;

    @Autowired private PostQueryService postQueryService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private long contestId;
    private long otherContestId;
    private long first;
    private long second;
    private long endsToday;
    private long newest;
    private long periodEnded;
    private long completed;

    @BeforeEach
    void setUp() {
        contestId = insertContest("https://test.local/contest-recruiting-a");
        otherContestId = insertContest("https://test.local/contest-recruiting-b");
        String today = LocalDate.now(ZoneId.of("Asia/Seoul")).toString();
        first = insert(contestId, "RECRUITING", "2099-12-01", "2026-11-01 10:00:00");
        second = insert(contestId, "RECRUITING", "2099-12-01", "2026-11-02 10:00:00");
        // 종료일 당일까지는 지원할 수 있다 (REC-001)
        endsToday = insert(contestId, "RECRUITING", today, "2026-11-03 10:00:00");
        newest = insert(contestId, "RECRUITING", "2099-12-01", "2026-11-05 10:00:00");
        // 종료일이 지난 「모집마감」 글과 모집완료 글은 「팀원 모집 중」 칸에서 빠진다
        periodEnded = insert(contestId, "RECRUITING", "2000-01-01", "2026-11-04 10:00:00");
        completed = insert(contestId, "COMPLETED", "2099-12-01", "2026-11-06 10:00:00");
        long deleted = insert(contestId, "RECRUITING", "2099-12-01", "2026-11-07 10:00:00");
        jdbcTemplate.update("UPDATE recruit_post SET deleted_at = NOW() WHERE id = ?", deleted);
        insert(otherContestId, "RECRUITING", "2099-12-01", "2026-11-08 10:00:00");
    }

    private long insertContest(String sourceUrl) {
        jdbcTemplate.update(
                "INSERT INTO contest (source_url, source_site, title, category_id, end_date)"
                        + " VALUES (?, 'WEVITY', '팀원 모집 칸 공모전', 1, '2099-12-31')",
                sourceUrl);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long insert(long contest, String status, String endDate, String createdAt) {
        jdbcTemplate.update(
                "INSERT INTO recruit_post (author_id, contest_id, title, content, target_count, status,"
                        + " recruit_start_date, recruit_end_date, created_at)"
                        + " VALUES (?, ?, '팀원 모집 칸 테스트', '내용', 4, ?, '2026-11-01', ?, ?)",
                AUTHOR_ID,
                contest,
                status,
                endDate,
                createdAt);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update(
                "DELETE FROM recruit_post WHERE contest_id IN (?, ?)", contestId, otherContestId);
        jdbcTemplate.update("DELETE FROM contest WHERE id IN (?, ?)", contestId, otherContestId);
    }

    @Test
    @DisplayName("공모전 상세 칸: 지원할 수 있는 글만 최신 등록순 3개, 전체 개수는 4 (MAIN-02 ⑨)")
    void 지원_가능한_글만_최신순_3개() {
        PageResponse<PostListItemResponse> result =
                postQueryService.getPosts(contestId, true, 0, 3);

        assertThat(result.items())
                .extracting(PostListItemResponse::postId)
                .containsExactly(newest, endsToday, second);
        assertThat(result.totalCount()).isEqualTo(4);
        assertThat(result.hasNext()).isTrue();
        assertThat(postQueryService.getPosts(contestId, true, 1, 3).items())
                .extracting(PostListItemResponse::postId)
                .containsExactly(first);
    }

    @Test
    @DisplayName("「더 보기」 목록: 그 공모전 글을 모집마감·모집완료까지 최신순으로, 삭제·다른 공모전 글은 빼고 (PST-001 ⑩)")
    void 공모전_글을_모집완료까지_최신순() {
        PageResponse<PostListItemResponse> result =
                postQueryService.getPosts(contestId, false, 0, 20);

        assertThat(result.items())
                .extracting(PostListItemResponse::postId)
                .containsExactly(completed, newest, periodEnded, endsToday, second, first);
    }
}
