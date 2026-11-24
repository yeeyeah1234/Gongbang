package com.gongbang.member.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.gongbang.application.service.MyApplicationListService;
import com.gongbang.board.service.MyBoardPostService;
import com.gongbang.comment.service.MyCommentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** 실제 MySQL에서 본인 필터·삭제 정책·댓글 대상·페이지 수를 검증. 테스트 데이터는 롤백한다. */
@SpringBootTest(
        properties = {
            "spring.autoconfigure.exclude=",
            "spring.datasource.url=${TEST_DATASOURCE_URL:}",
            "spring.datasource.username=${TEST_DATASOURCE_USERNAME:root}",
            "spring.datasource.password=${TEST_DATASOURCE_PASSWORD:}",
            "app.profile-storage=local"
        })
@EnabledIfEnvironmentVariable(named = "TEST_DATASOURCE_URL", matches = ".*_test.*")
@Transactional
class MyActivityMysqlTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired MyCommentService comments;
    @Autowired MyApplicationListService applications;
    @Autowired MyBoardPostService boards;
    long me, other, post, board;

    long insert(String sql, Object... args) {
        jdbc.update(sql, args);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    @BeforeEach
    void seed() {
        me = insert("INSERT INTO member (status) VALUES ('ACTIVE')");
        other = insert("INSERT INTO member (status) VALUES ('ACTIVE')");
        long category = jdbc.queryForObject("SELECT MIN(id) FROM category", Long.class);
        post =
                insert(
                        "INSERT INTO recruit_post (author_id, ext_contest_title, ext_category_id, ext_deadline, title, content, target_count, recruit_start_date, recruit_end_date) VALUES (?, 'test', ?, '2099-12-31', 'recruit', 'body', 4, '2026-01-01', '2099-12-01')",
                        other,
                        category);
        board =
                insert(
                        "INSERT INTO board_post (author_id,category,title,content) VALUES (?, 'FREE','board','body')",
                        me);
    }

    @Test
    void ownCommentsIncludeBothTargetsAndExcludeDeleted() {
        insert(
                "INSERT INTO comment (post_id,author_id,content,is_secret) VALUES (?,?,'mine secret',TRUE)",
                post,
                me);
        long boardComment =
                insert(
                        "INSERT INTO comment (board_post_id,author_id,content) VALUES (?,?,'mine board')",
                        board,
                        me);
        insert("INSERT INTO comment (post_id,author_id,content) VALUES (?,?,'other')", post, other);
        insert(
                "INSERT INTO comment (post_id,author_id,content,deleted_at) VALUES (?,?,'hidden',NOW())",
                post,
                me);
        var first = comments.list(me, 0, 1);
        assertThat(first.totalCount()).isEqualTo(2);
        assertThat(first.hasNext()).isTrue();
        assertThat(first.items().get(0).getCommentId()).isEqualTo(boardComment);
        assertThat(first.items().get(0).getBoardPostId()).isEqualTo(board);
        assertThat(comments.list(me, 1, 1).items().get(0).getContent()).isEqualTo("mine secret");
        jdbc.update("UPDATE board_post SET deleted_at=NOW() WHERE id=?", board);
        assertThat(comments.list(me, 0, 10).totalCount()).isEqualTo(1);
        jdbc.update("UPDATE recruit_post SET deleted_at=NOW() WHERE id=?", post);
        assertThat(comments.list(me, 0, 10).items()).isEmpty();
    }

    @Test
    void applicationHistoryKeepsDeletedTargetButMasksTitle() {
        insert(
                "INSERT INTO application (post_id,applicant_id,status) VALUES (?,?,'PENDING')",
                post,
                me);
        insert(
                "INSERT INTO application (post_id,applicant_id,status) VALUES (?,?,'PENDING')",
                post,
                other);
        var before = applications.list(me, 0, 10);
        assertThat(before.totalCount()).isEqualTo(1);
        assertThat(before.items().get(0).getStatus()).isEqualTo("PENDING");
        jdbc.update("UPDATE recruit_post SET deleted_at=NOW() WHERE id=?", post);
        var after = applications.list(me, 0, 10).items().get(0);
        assertThat(after.getDeletedAt()).isNotNull();
        assertThat(after.getTitle()).isEqualTo("삭제된 모집글");
        jdbc.update("DELETE FROM application WHERE applicant_id=?", me);
        assertThat(applications.list(me, 0, 10).items()).isEmpty();
    }

    @Test
    void boardListOnlyContainsOwnActivePosts() {
        insert(
                "INSERT INTO board_post (author_id,category,title,content) VALUES (?, 'FREE','other','body')",
                other);
        assertThat(boards.list(me, 0, 10).totalCount()).isEqualTo(1);
        assertThat(boards.list(me, 0, 10).items().get(0).getBoardPostId()).isEqualTo(board);
        jdbc.update("UPDATE board_post SET deleted_at=NOW() WHERE id=?", board);
        assertThat(boards.list(me, 0, 10).items()).isEmpty();
    }
}
