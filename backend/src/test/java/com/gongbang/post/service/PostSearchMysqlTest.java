package com.gongbang.post.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.gongbang.post.dto.PostListItemResponse;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 통합 검색 모집글 부분(SRCH-05 ①, SRCH-02 ②)이 실제 MySQL에서 찾는 글과 정렬을 맞게 고르는지 확인한다.
 *
 * <p>환경 변수 TEST_DATASOURCE_URL이 있을 때만 돈다 (PostDecreaseCountMysqlTest와 같은 방식). 반드시 테스트 전용 스키마(이름에
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
class PostSearchMysqlTest {

    // 다른 테스트 데이터와 겹치지 않게 큰 번호와 다른 글에 없을 검색어를 쓴다
    private static final long AUTHOR_ID = 940_001L;
    private static final long NICKNAME_AUTHOR_ID = 940_009L;
    private static final String KEYWORD = "검색확인XQ";

    @Autowired private PostQueryService postQueryService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private long contestId;
    private long exact;
    private long startsWith;
    private long contains;
    private long contentOnly;
    private long byAuthor;
    private long deleted;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update(
                "INSERT INTO contest (source_url, source_site, title, category_id, end_date)"
                        + " VALUES ('https://test.local/post-search-mysql', 'WEVITY', '검색 테스트 공모전', 1,"
                        + " '2099-12-31')");
        contestId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        // 오래된 글부터 넣는다. 정확도가 같으면 최신순이므로 작성 시각을 일부러 섞는다
        exact = insert(KEYWORD, "내용", "COMPLETED", "2099-01-01", "2026-11-01 10:00:00", AUTHOR_ID);
        startsWith =
                insert(
                        KEYWORD + " 팀원 모집",
                        "내용",
                        "RECRUITING",
                        "2099-01-10",
                        "2026-11-02 10:00:00",
                        AUTHOR_ID);
        contains =
                insert(
                        "같이 할 " + KEYWORD,
                        "내용",
                        "RECRUITING",
                        "2099-01-05",
                        "2026-11-03 10:00:00",
                        AUTHOR_ID);
        contentOnly =
                insert(
                        "본문에만 있는 글",
                        "설명에 " + KEYWORD + " 포함",
                        "RECRUITING",
                        "2000-01-01",
                        "2026-11-04 10:00:00",
                        AUTHOR_ID);
        byAuthor =
                insert(
                        "작성자로만 찾는 글",
                        "내용",
                        "RECRUITING",
                        "2099-01-20",
                        "2026-11-05 10:00:00",
                        NICKNAME_AUTHOR_ID);
        deleted =
                insert(KEYWORD, "내용", "RECRUITING", "2099-01-02", "2026-11-06 10:00:00", AUTHOR_ID);
        jdbcTemplate.update("UPDATE recruit_post SET deleted_at = NOW() WHERE id = ?", deleted);
    }

    private long insert(
            String title,
            String content,
            String status,
            String endDate,
            String createdAt,
            long authorId) {
        jdbcTemplate.update(
                "INSERT INTO recruit_post (author_id, contest_id, title, content, target_count, status,"
                        + " recruit_start_date, recruit_end_date, created_at)"
                        + " VALUES (?, ?, ?, ?, 4, ?, '2026-11-01', ?, ?)",
                authorId,
                contestId,
                title,
                content,
                status,
                endDate,
                createdAt);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM recruit_post WHERE contest_id = ?", contestId);
        jdbcTemplate.update("DELETE FROM contest WHERE id = ?", contestId);
    }

    private List<Long> ids(String sort, Set<Long> authorIds) {
        return postQueryService.searchPosts(KEYWORD, authorIds, sort, 0, 20).items().stream()
                .map(PostListItemResponse::postId)
                .toList();
    }

    @Test
    @DisplayName("정확도순: 제목 같음 > 제목 시작 > 제목 포함 > 본문·작성자만 일치, 삭제된 글은 빠진다")
    void 정확도순() {
        assertThat(ids(null, Set.of()))
                .containsExactly(exact, startsWith, contains, contentOnly)
                .doesNotContain(deleted, byAuthor);
        // 작성자 ID로도 찾으면 점수가 가장 낮은 칸에 최신순으로 들어간다
        assertThat(ids("accuracy", Set.of(NICKNAME_AUTHOR_ID)))
                .containsExactly(exact, startsWith, contains, byAuthor, contentOnly);
        assertThat(
                        postQueryService
                                .searchPosts(KEYWORD, Set.of(NICKNAME_AUTHOR_ID), null, 0, 2)
                                .totalCount())
                .isEqualTo(5);
    }

    @Test
    @DisplayName("최신순: 작성 시각 내림차순")
    void 최신순() {
        assertThat(ids("latest", Set.of(NICKNAME_AUTHOR_ID)))
                .containsExactly(byAuthor, contentOnly, contains, startsWith, exact);
    }

    @Test
    @DisplayName("마감 임박순: 지원할 수 있는 글을 모집 종료일 빠른 순으로 앞에, 모집완료·기간 지난 글은 뒤에 최신순")
    void 마감_임박순() {
        assertThat(ids("deadline", Set.of(NICKNAME_AUTHOR_ID)))
                .containsExactly(contains, startsWith, byAuthor, contentOnly, exact);
    }
}
