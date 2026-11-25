package com.gongbang.board.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.gongbang.board.dto.BoardPostListItemResponse;
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
 * 통합 검색 커뮤니티 게시글 부분(SRCH-05 ①, SRCH-02 ②)이 실제 MySQL에서 찾는 글과 정렬을 맞게 고르는지 확인한다.
 *
 * <p>환경 변수 TEST_DATASOURCE_URL이 있을 때만 돈다 (BoardPostMysqlTest와 같은 방식). 반드시 테스트 전용 스키마(이름에 _test)를
 * 가리키게 한다. 테스트가 끝나면 이 테스트가 넣은 게시글 행을 지운다.
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
class BoardPostSearchMysqlTest {

    // 다른 테스트 데이터와 겹치지 않게 큰 번호와 다른 글에 없을 검색어를 쓴다 (BoardPostMysqlTest는 940_001을 씀)
    private static final long AUTHOR_ID = 950_001L;
    private static final long NICKNAME_AUTHOR_ID = 950_009L;
    private static final String KEYWORD = "게시판검색XQ";

    @Autowired private BoardPostQueryService queryService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private long exact;
    private long startsWith;
    private long contains;
    private long contentOnly;
    private long byAuthor;
    private long deleted;

    @BeforeEach
    void setUp() {
        // 정확도가 같으면 최신순이므로 작성 시각을 일부러 섞는다
        contains = insert("같이 본 " + KEYWORD, "내용", "2026-11-01 10:00:00", AUTHOR_ID);
        exact = insert(KEYWORD, "내용", "2026-11-02 10:00:00", AUTHOR_ID);
        contentOnly = insert("내용에만 있는 글", "후기 " + KEYWORD, "2026-11-03 10:00:00", AUTHOR_ID);
        startsWith = insert(KEYWORD + " 후기", "내용", "2026-11-04 10:00:00", AUTHOR_ID);
        byAuthor = insert("작성자로만 찾는 글", "내용", "2026-11-05 10:00:00", NICKNAME_AUTHOR_ID);
        deleted = insert(KEYWORD, "내용", "2026-11-06 10:00:00", AUTHOR_ID);
        jdbcTemplate.update("UPDATE board_post SET deleted_at = NOW() WHERE id = ?", deleted);
    }

    private long insert(String title, String content, String createdAt, long authorId) {
        jdbcTemplate.update(
                "INSERT INTO board_post (author_id, category, title, content, created_at)"
                        + " VALUES (?, 'TIP', ?, ?, ?)",
                authorId,
                title,
                content,
                createdAt);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update(
                "DELETE FROM board_post WHERE author_id IN (?, ?)", AUTHOR_ID, NICKNAME_AUTHOR_ID);
    }

    private List<Long> ids(String sort, Set<Long> authorIds) {
        return queryService.searchPosts(KEYWORD, authorIds, sort, 0, 20).items().stream()
                .map(BoardPostListItemResponse::boardPostId)
                .toList();
    }

    @Test
    @DisplayName("정확도순: 제목 같음 > 제목 시작 > 제목 포함 > 내용·작성자만 일치, 삭제된 글은 빠진다")
    void 정확도순() {
        assertThat(ids(null, Set.of()))
                .containsExactly(exact, startsWith, contains, contentOnly)
                .doesNotContain(deleted, byAuthor);
        // 작성자 ID로도 찾으면 점수가 가장 낮은 칸에 최신순으로 들어간다
        assertThat(ids("accuracy", Set.of(NICKNAME_AUTHOR_ID)))
                .containsExactly(exact, startsWith, contains, byAuthor, contentOnly);
        assertThat(
                        queryService
                                .searchPosts(KEYWORD, Set.of(NICKNAME_AUTHOR_ID), null, 0, 2)
                                .totalCount())
                .isEqualTo(5);
    }

    @Test
    @DisplayName("최신순과 마감 임박순(게시글은 마감일이 없어 최신순)은 작성 시각 내림차순")
    void 최신순과_마감_임박순() {
        List<Long> latest = List.of(byAuthor, startsWith, contentOnly, exact, contains);
        assertThat(ids("latest", Set.of(NICKNAME_AUTHOR_ID))).containsExactlyElementsOf(latest);
        assertThat(ids("deadline", Set.of(NICKNAME_AUTHOR_ID))).containsExactlyElementsOf(latest);
    }
}
