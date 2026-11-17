package com.gongbang.post.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 팀원이 동시에 나갈 때 확정 인원 감소가 빠지지 않는지 실제 MySQL에서 확인한다 (AGENTS.md 7 「동시 요청 테스트」, INT-006 ④⑤).
 *
 * <p>환경 변수 TEST_DATASOURCE_URL이 있을 때만 돈다 (ApplicationAcceptMysqlTest와 같은 방식). 반드시 테스트 전용 스키마(이름에
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
class PostDecreaseCountMysqlTest {

    // 다른 테스트 데이터와 겹치지 않게 큰 번호를 쓴다
    private static final long AUTHOR_ID = 920_001L;

    @Autowired private PostCommandService postCommandService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private long contestId;
    private long postId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update(
                "INSERT INTO contest (source_url, source_site, title, category_id, end_date)"
                        + " VALUES ('https://test.local/decrease-mysql', 'WEVITY', '동시 감소 공모전', 1,"
                        + " '2099-12-31')");
        contestId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        // 목표 4명이 모두 찬 모집완료 글
        jdbcTemplate.update(
                "INSERT INTO recruit_post (author_id, contest_id, title, content, target_count,"
                        + " current_count, status, completed_at, recruit_start_date, recruit_end_date)"
                        + " VALUES (?, ?, '동시 감소 테스트 모집글', '내용', 4, 4, 'COMPLETED', NOW(),"
                        + " '2026-11-01', '2099-12-01')",
                AUTHOR_ID,
                contestId);
        postId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM recruit_post WHERE id = ?", postId);
        jdbcTemplate.update("DELETE FROM contest WHERE id = ?", contestId);
    }

    @Test
    @DisplayName("4/4 모집완료 글에서 팀원 3명이 동시에 나가면 1/4가 되고 모집완료는 그대로 (INT-006 ④⑤)")
    void 동시에_나가도_인원이_빠짐없이_줄어든다() throws Exception {
        int threads = 3;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        ConcurrentLinkedQueue<Integer> results = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<Throwable> unexpected = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < threads; i++) {
            executor.submit(
                    () -> {
                        ready.countDown();
                        try {
                            start.await();
                            results.add(postCommandService.decreaseCurrentCount(postId));
                        } catch (Throwable t) {
                            unexpected.add(t);
                        } finally {
                            done.countDown();
                        }
                    });
        }
        ready.await();
        start.countDown();
        assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        assertThat(unexpected).isEmpty();
        // 잠금 때문에 하나씩 처리되어 각 호출이 서로 다른 인원을 돌려받는다
        assertThat(results).containsExactlyInAnyOrder(3, 2, 1);

        Map<String, Object> post =
                jdbcTemplate.queryForMap(
                        "SELECT current_count, status FROM recruit_post WHERE id = ?", postId);
        assertThat(((Number) post.get("current_count")).intValue()).isEqualTo(1);
        assertThat(post.get("status")).isEqualTo("COMPLETED");
    }
}
