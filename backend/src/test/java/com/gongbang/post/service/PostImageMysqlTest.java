package com.gongbang.post.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

/**
 * PST-007 ②: 사진을 동시에 올려도 3장을 넘지 않는지 실제 MySQL에서 확인한다 (AGENTS.md 7 「동시 요청 테스트」).
 *
 * <p>환경 변수 TEST_DATASOURCE_URL이 있을 때만 돈다 (다른 *MysqlTest와 같은 방식). 반드시 테스트 전용 스키마(이름에 _test)를 가리키게
 * 한다. 사진 파일은 임시 폴더에 저장하고, 끝나면 이 테스트가 넣은 행과 파일을 지운다.
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
            "app.upload-dir=${java.io.tmpdir}/gongbang-post-image-mysql-test"
        })
@EnabledIfEnvironmentVariable(named = "TEST_DATASOURCE_URL", matches = ".*_test.*")
class PostImageMysqlTest {

    // 다른 테스트 데이터와 겹치지 않게 큰 번호를 쓴다
    private static final long AUTHOR_ID = 930_001L;
    private static final Path UPLOAD_DIR =
            Paths.get(
                    System.getProperty("java.io.tmpdir"), "gongbang-post-image-mysql-test", "post");

    @Autowired private PostImageService postImageService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private long postId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update(
                "INSERT INTO recruit_post (author_id, ext_contest_title, ext_category_id, ext_deadline,"
                        + " title, content, target_count, recruit_start_date, recruit_end_date)"
                        + " VALUES (?, '동시 사진 공모전', 1, '2099-12-31', '동시 사진 테스트 모집글', '내용', 4,"
                        + " '2026-11-01', '2099-12-01')",
                AUTHOR_ID);
        postId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    @AfterEach
    void tearDown() throws IOException {
        jdbcTemplate.update("DELETE FROM recruit_post_image WHERE post_id = ?", postId);
        jdbcTemplate.update("DELETE FROM recruit_post WHERE id = ?", postId);
        if (Files.isDirectory(UPLOAD_DIR)) {
            try (Stream<Path> files = Files.list(UPLOAD_DIR)) {
                for (Path file :
                        files.filter(f -> f.getFileName().toString().startsWith(postId + "-"))
                                .toList()) {
                    Files.deleteIfExists(file);
                }
            }
        }
    }

    private static MockMultipartFile png() {
        byte[] bytes = new byte[64];
        System.arraycopy(PostImageTypeTest.PNG, 0, bytes, 0, PostImageTypeTest.PNG.length);
        return new MockMultipartFile("file", "poster.png", "image/png", bytes);
    }

    @Test
    @DisplayName("사진 5장을 동시에 올리면 3장만 저장되고 2장은 409 POST_IMAGE_LIMIT, 순서는 0·1·2 (PST-007 ②③)")
    void 동시에_올려도_세_장까지만() throws Exception {
        int threads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        ConcurrentLinkedQueue<ErrorCode> failures = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<Throwable> unexpected = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < threads; i++) {
            executor.submit(
                    () -> {
                        ready.countDown();
                        try {
                            start.await();
                            postImageService.upload(AUTHOR_ID, postId, png());
                        } catch (BusinessException e) {
                            failures.add(e.getErrorCode());
                        } catch (Throwable e) {
                            unexpected.add(e);
                        } finally {
                            done.countDown();
                        }
                    });
        }
        ready.await(10, TimeUnit.SECONDS);
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        assertThat(unexpected).isEmpty();
        assertThat(failures)
                .containsExactly(ErrorCode.POST_IMAGE_LIMIT, ErrorCode.POST_IMAGE_LIMIT);
        List<Integer> orders =
                jdbcTemplate.queryForList(
                        "SELECT sort_order FROM recruit_post_image"
                                + " WHERE post_id = ? AND deleted_at IS NULL ORDER BY sort_order",
                        Integer.class,
                        postId);
        assertThat(orders).containsExactly(0, 1, 2);
        // 막힌 요청은 파일을 저장하지 않는다
        try (Stream<Path> files = Files.list(UPLOAD_DIR)) {
            assertThat(
                            files.filter(f -> f.getFileName().toString().startsWith(postId + "-"))
                                    .count())
                    .isEqualTo(3);
        }
    }

    @Test
    @DisplayName("지운 사진은 행이 남고(소프트 삭제) 파일도 남으며, 그 자리에 새 사진을 올릴 수 있다 (PST-007 ④)")
    void 삭제_후_다시_올리기() throws Exception {
        Long first = postImageService.upload(AUTHOR_ID, postId, png()).images().get(0).imageId();
        postImageService.upload(AUTHOR_ID, postId, png());
        postImageService.upload(AUTHOR_ID, postId, png());

        postImageService.delete(AUTHOR_ID, postId, first);
        assertThat(postImageService.upload(AUTHOR_ID, postId, png()).images()).hasSize(3);

        Integer total =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM recruit_post_image WHERE post_id = ?",
                        Integer.class,
                        postId);
        String deletedUrl =
                jdbcTemplate.queryForObject(
                        "SELECT image_url FROM recruit_post_image WHERE id = ? AND deleted_at IS NOT NULL",
                        String.class,
                        first);
        assertThat(total).isEqualTo(4);
        assertThat(
                        UPLOAD_DIR.resolve(
                                deletedUrl.substring(LocalPostImageStorage.URL_PREFIX.length())))
                .exists();
    }
}
