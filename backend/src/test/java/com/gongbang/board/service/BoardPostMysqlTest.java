package com.gongbang.board.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.gongbang.board.domain.BoardCategory;
import com.gongbang.board.dto.BoardPostListItemResponse;
import com.gongbang.board.dto.BoardPostRequest;
import java.time.LocalDateTime;
import java.util.List;
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
 * BRD 커뮤니티 게시판을 실제 MySQL에서 확인한다: 좋아요 동시 요청(AGENTS.md 7), 목록·인기글 쿼리, 조회수.
 *
 * <p>환경 변수 TEST_DATASOURCE_URL이 있을 때만 돈다 (다른 *MysqlTest와 같은 방식). 반드시 테스트 전용 스키마(이름에 _test)를 가리키게
 * 한다. 끝나면 이 테스트가 넣은 게시글·좋아요·조회 기록만 지운다.
 */
@SpringBootTest(
        properties = {
            // test application.yml이 끈 DataSource·JPA·Flyway 자동 설정을 이 테스트에서만 다시 켠다.
            "spring.autoconfigure.exclude=",
            "spring.datasource.url=${TEST_DATASOURCE_URL:}",
            "spring.datasource.username=${TEST_DATASOURCE_USERNAME:root}",
            "spring.datasource.password=${TEST_DATASOURCE_PASSWORD:}",
            "spring.jpa.properties.hibernate.show_sql=false",
            "app.profile-storage=local",
            "app.upload-dir=${java.io.tmpdir}/gongbang-board-mysql-test"
        })
@EnabledIfEnvironmentVariable(named = "TEST_DATASOURCE_URL", matches = ".*_test.*")
class BoardPostMysqlTest {

    // 다른 테스트 데이터와 겹치지 않게 큰 번호를 쓴다 (board_post.author_id는 FK가 없다)
    private static final long AUTHOR_ID = 940_001L;
    private static final long FIRST_LIKER_ID = 940_100L;

    @Autowired private BoardPostCommandService commandService;
    @Autowired private BoardPostQueryService queryService;
    @Autowired private BoardPostImageService imageService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        String mine =
                "(SELECT id FROM (SELECT id FROM board_post WHERE author_id = "
                        + AUTHOR_ID
                        + ") t)";
        jdbcTemplate.update("DELETE FROM board_post_like WHERE board_post_id IN " + mine);
        jdbcTemplate.update("DELETE FROM board_post_view WHERE board_post_id IN " + mine);
        jdbcTemplate.update("DELETE FROM board_post_image WHERE board_post_id IN " + mine);
        jdbcTemplate.update("DELETE FROM board_post WHERE author_id = ?", AUTHOR_ID);
    }

    private long write(BoardCategory category, String title) {
        return commandService
                .create(AUTHOR_ID, new BoardPostRequest(category, null, title, "내용", null))
                .boardPostId();
    }

    private int likeCount(long boardPostId) {
        return jdbcTemplate.queryForObject(
                "SELECT like_count FROM board_post WHERE id = ?", Integer.class, boardPostId);
    }

    private int likeRows(long boardPostId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM board_post_like WHERE board_post_id = ?",
                Integer.class,
                boardPostId);
    }

    private void concurrently(int threads, IntConsumerWithIndex task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();
        for (int i = 0; i < threads; i++) {
            int index = i;
            executor.submit(
                    () -> {
                        ready.countDown();
                        try {
                            start.await();
                            task.run(index);
                        } catch (Throwable e) {
                            errors.add(e);
                        } finally {
                            done.countDown();
                        }
                    });
        }
        ready.await(10, TimeUnit.SECONDS);
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();
        assertThat(errors).isEmpty();
    }

    @FunctionalInterface
    private interface IntConsumerWithIndex {
        void run(int index) throws Exception;
    }

    @Test
    @DisplayName("회원 10명이 동시에 좋아요를 누르면 like_count가 정확히 10이고 좋아요 행도 10개 (BRD-007)")
    void 동시_좋아요() throws Exception {
        long postId = write(BoardCategory.TIP, "동시 좋아요");

        concurrently(10, i -> commandService.like(FIRST_LIKER_ID + i, postId));

        assertThat(likeCount(postId)).isEqualTo(10);
        assertThat(likeRows(postId)).isEqualTo(10);
    }

    @Test
    @DisplayName("같은 회원이 좋아요·취소를 동시에 여러 번 보내도 like_count와 좋아요 행 수가 항상 같다")
    void 동시_좋아요와_취소() throws Exception {
        long postId = write(BoardCategory.TIP, "좋아요 취소 섞기");

        concurrently(
                12,
                i -> {
                    if (i % 2 == 0) commandService.like(FIRST_LIKER_ID, postId);
                    else commandService.unlike(FIRST_LIKER_ID, postId);
                });

        assertThat(likeCount(postId)).isEqualTo(likeRows(postId)).isBetween(0, 1);
    }

    @Test
    @DisplayName("목록은 말머리로 거르고 인기순 정렬이 되며, 인기글은 최근 7일·좋아요 1개 이상만 고른다 (BRD-001, BRD-008)")
    void 목록과_인기글() {
        long tipFew = write(BoardCategory.TIP, "노하우 좋아요 1");
        long tipMany = write(BoardCategory.TIP, "노하우 좋아요 2");
        long showcase = write(BoardCategory.SHOWCASE, "결과물 좋아요 0");
        long old = write(BoardCategory.TIP, "오래된 노하우");
        commandService.like(FIRST_LIKER_ID, tipFew);
        commandService.like(FIRST_LIKER_ID, tipMany);
        commandService.like(FIRST_LIKER_ID + 1, tipMany);
        commandService.like(FIRST_LIKER_ID, old);
        commandService.like(FIRST_LIKER_ID + 1, old);
        commandService.like(FIRST_LIKER_ID + 2, old);
        jdbcTemplate.update(
                "UPDATE board_post SET created_at = ? WHERE id = ?",
                LocalDateTime.now().minusDays(8),
                old);

        List<Long> tipPopularOrder =
                queryService.getPosts(BoardCategory.TIP, null, "popular", 0, 50).items().stream()
                        .map(BoardPostListItemResponse::boardPostId)
                        .filter(id -> id == tipFew || id == tipMany || id == old)
                        .toList();
        assertThat(tipPopularOrder).containsExactly(old, tipMany, tipFew);
        assertThat(
                        queryService
                                .getPosts(BoardCategory.SHOWCASE, null, null, 0, 50)
                                .items()
                                .stream()
                                .map(BoardPostListItemResponse::boardPostId))
                .contains(showcase)
                .doesNotContain(tipFew);

        List<Long> popular =
                queryService.getPopular(BoardCategory.TIP).stream()
                        .map(BoardPostListItemResponse::boardPostId)
                        .filter(id -> id == tipFew || id == tipMany || id == old)
                        .toList();
        // 8일 전 글은 빠지고, 좋아요가 많은 순
        assertThat(popular).containsExactly(tipMany, tipFew);
        assertThat(
                        queryService.getPopular(BoardCategory.SHOWCASE).stream()
                                .map(BoardPostListItemResponse::boardPostId))
                .doesNotContain(showcase);
    }

    @Test
    @DisplayName("조회수: 같은 회원은 24시간에 한 번, 다른 회원은 따로, 비회원은 올리지 않는다 (BRD-004 ⑤)")
    void 조회수() {
        long postId = write(BoardCategory.TIP, "조회수");

        queryService.getPost(postId, FIRST_LIKER_ID);
        queryService.getPost(postId, FIRST_LIKER_ID);
        queryService.getPost(postId, FIRST_LIKER_ID + 1);
        queryService.getPost(postId, null);

        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT view_count FROM board_post WHERE id = ?",
                                Integer.class,
                                postId))
                .isEqualTo(2);
    }

    private static org.springframework.mock.web.MockMultipartFile png() {
        byte[] bytes = new byte[64];
        byte[] head = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
        System.arraycopy(head, 0, bytes, 0, head.length);
        return new org.springframework.mock.web.MockMultipartFile(
                "file", "work.png", "image/png", bytes);
    }

    @Test
    @DisplayName("결과물 사진 5장을 동시에 올리면 3장만 저장되고 순서는 0·1·2 (BRD-006 ②③)")
    void 동시_사진_올리기() throws Exception {
        long postId = write(BoardCategory.SHOWCASE, "동시 사진");
        java.util.concurrent.atomic.AtomicInteger limited =
                new java.util.concurrent.atomic.AtomicInteger();

        concurrently(
                5,
                i -> {
                    try {
                        imageService.upload(AUTHOR_ID, postId, png());
                    } catch (com.gongbang.global.error.exception.BusinessException e) {
                        assertThat(e.getErrorCode())
                                .isEqualTo(
                                        com.gongbang.global.error.ErrorCode.BOARD_POST_IMAGE_LIMIT);
                        limited.incrementAndGet();
                    }
                });

        assertThat(limited.get()).isEqualTo(2);
        assertThat(
                        jdbcTemplate.queryForList(
                                "SELECT sort_order FROM board_post_image WHERE board_post_id = ?"
                                        + " AND deleted_at IS NULL ORDER BY sort_order",
                                Integer.class,
                                postId))
                .containsExactly(0, 1, 2);
    }

    @Test
    @DisplayName("결과물 자랑을 노하우로 바꾸면 링크와 사진이 지워지고(사진은 소프트 삭제) 상세에 사진이 없다 (BRD-003 ③)")
    void 말머리_변경과_사진() {
        long postId =
                commandService
                        .create(
                                AUTHOR_ID,
                                new BoardPostRequest(
                                        BoardCategory.SHOWCASE,
                                        null,
                                        "결과물",
                                        "내용",
                                        "https://demo.example.com"))
                        .boardPostId();
        imageService.upload(AUTHOR_ID, postId, png());
        assertThat(queryService.getPost(postId, null).images()).hasSize(1);

        commandService.update(
                AUTHOR_ID,
                postId,
                new BoardPostRequest(BoardCategory.TIP, null, "노하우", "내용", null));

        assertThat(queryService.getPost(postId, null).images()).isEmpty();
        assertThat(queryService.getPost(postId, null).linkUrl()).isNull();
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM board_post_image WHERE board_post_id = ?"
                                        + " AND deleted_at IS NOT NULL",
                                Integer.class,
                                postId))
                .isEqualTo(1);
    }
}
