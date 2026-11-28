package com.gongbang.report.service;

import static org.assertj.core.api.Assertions.*;

import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.notification.service.NotificationService;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(
        properties = {
            "spring.autoconfigure.exclude=",
            "spring.datasource.url=${TEST_DATASOURCE_URL:}",
            "spring.datasource.username=${TEST_DATASOURCE_USERNAME:root}",
            "spring.datasource.password=${TEST_DATASOURCE_PASSWORD:}",
            "app.profile-storage=local"
        })
@EnabledIfEnvironmentVariable(named = "TEST_DATASOURCE_URL", matches = ".*_test.*")
class AdminReportCancellationMysqlTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired AdminReportService service;
    @Autowired PlatformTransactionManager transactions;
    @MockBean NotificationService notifications;

    record Fixture(long actor, long member, long post, long first, long second) {}

    long member(String role) {
        String email = UUID.randomUUID() + "@test.local";
        jdbc.update(
                "INSERT INTO member(email,nickname,status,role) VALUES (?,?,'ACTIVE',?)",
                email,
                "t" + UUID.randomUUID().toString().substring(0, 8),
                role);
        return jdbc.queryForObject("SELECT id FROM member WHERE email=?", Long.class, email);
    }

    Fixture fixture() {
        long actor = member("ADMIN"), author = member("USER");
        String title = UUID.randomUUID().toString();
        jdbc.update(
                "INSERT INTO board_post(author_id,category,title,content) VALUES (?,'TIP',?,'content')",
                author,
                title);
        long post =
                jdbc.queryForObject("SELECT id FROM board_post WHERE title=?", Long.class, title);
        jdbc.update(
                "INSERT INTO report(reporter_id,reported_member_id,target_type,target_id,reason_code) VALUES (?,?,'BOARD_POST',?,'SPAM'), (?,?,'BOARD_POST',?,'SPAM')",
                actor,
                author,
                post,
                actor,
                author,
                post);
        var ids =
                jdbc.queryForList(
                        "SELECT id FROM report WHERE target_type='BOARD_POST' AND target_id=? ORDER BY id",
                        Long.class,
                        post);
        return new Fixture(actor, author, post, ids.get(0), ids.get(1));
    }

    void clean(Fixture f) {
        jdbc.update(
                "DELETE FROM report_review_cancellation WHERE report_id IN (?,?)",
                f.first,
                f.second);
        jdbc.update("DELETE FROM admin_activity WHERE actor_id=?", f.actor);
        jdbc.update("DELETE FROM report WHERE id IN (?,?)", f.first, f.second);
        jdbc.update("DELETE FROM board_post WHERE id=?", f.post);
        jdbc.update("DELETE FROM member WHERE id IN (?,?)", f.actor, f.member);
    }

    AdminReportService.Review hide() {
        return new AdminReportService.Review("HIDE_BOARD_POST", "moderation", null);
    }

    AdminReportService.Review cancel() {
        return new AdminReportService.Review(null, "mistake", null);
    }

    @Test
    void 새숨김신고가커밋될때까지기다린뒤복원을거절한다() throws Exception {
        var f = fixture();
        var executor = Executors.newFixedThreadPool(2);
        var hidden = new CountDownLatch(1);
        var commit = new CountDownLatch(1);
        try {
            service.review(f.actor, f.first, hide());
            var hiding =
                    executor.submit(
                            () ->
                                    new TransactionTemplate(transactions)
                                            .execute(
                                                    status -> {
                                                        service.review(f.actor, f.second, hide());
                                                        hidden.countDown();
                                                        try {
                                                            if (!commit.await(10, TimeUnit.SECONDS))
                                                                throw new IllegalStateException(
                                                                        "timeout");
                                                        } catch (InterruptedException e) {
                                                            Thread.currentThread().interrupt();
                                                            throw new IllegalStateException(e);
                                                        }
                                                        return true;
                                                    }));
            assertThat(hidden.await(10, TimeUnit.SECONDS)).isTrue();
            var started = new CountDownLatch(1);
            var canceling =
                    executor.submit(
                            () -> {
                                started.countDown();
                                return service.cancelReview(f.actor, f.first, cancel());
                            });
            assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> canceling.get(300, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
            commit.countDown();
            assertThat(hiding.get(10, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> canceling.get(10, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(BusinessException.class);
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT deleted_at IS NOT NULL FROM board_post WHERE id=?",
                                    Boolean.class,
                                    f.post))
                    .isTrue();
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT status FROM report WHERE id=?", String.class, f.first))
                    .isEqualTo("RESOLVED");
        } finally {
            commit.countDown();
            executor.shutdownNow();
            executor.awaitTermination(15, TimeUnit.SECONDS);
            clean(f);
        }
    }

    @Test
    void 동시에처리취소해도한번만복원하고취소이력을남긴다() throws Exception {
        var f = fixture();
        var executor = Executors.newFixedThreadPool(2);
        try {
            service.review(f.actor, f.first, hide());
            var start = new CountDownLatch(1);
            Callable<Boolean> task =
                    () -> {
                        start.await();
                        try {
                            service.cancelReview(f.actor, f.first, cancel());
                            return true;
                        } catch (BusinessException expected) {
                            return false;
                        }
                    };
            var a = executor.submit(task);
            var b = executor.submit(task);
            start.countDown();
            assertThat(
                            (a.get(10, TimeUnit.SECONDS) ? 1 : 0)
                                    + (b.get(10, TimeUnit.SECONDS) ? 1 : 0))
                    .isEqualTo(1);
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT deleted_at IS NULL FROM board_post WHERE id=?",
                                    Boolean.class,
                                    f.post))
                    .isTrue();
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT COUNT(*) FROM report_review_cancellation WHERE report_id=?",
                                    Integer.class,
                                    f.first))
                    .isEqualTo(1);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(15, TimeUnit.SECONDS);
            clean(f);
        }
    }

    @Test
    void 이미삭제된글의신고취소는글을복원하지않는다() {
        var f = fixture();
        try {
            jdbc.update(
                    "UPDATE board_post SET deleted_at='2026-11-01 09:00:00' WHERE id=?", f.post);
            service.review(f.actor, f.first, hide());
            service.cancelReview(f.actor, f.first, cancel());
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT CAST(deleted_at AS CHAR) FROM board_post WHERE id=?",
                                    String.class,
                                    f.post))
                    .isEqualTo("2026-11-01 09:00:00");
        } finally {
            clean(f);
        }
    }
}
