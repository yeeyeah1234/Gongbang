package com.gongbang.member.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.gongbang.notification.service.NotificationService;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(
        properties = {
            "spring.autoconfigure.exclude=",
            "spring.datasource.url=${TEST_DATASOURCE_URL:}",
            "spring.datasource.username=${TEST_DATASOURCE_USERNAME:root}",
            "spring.datasource.password=${TEST_DATASOURCE_PASSWORD:}",
            "app.profile-storage=local"
        })
@EnabledIfEnvironmentVariable(named = "TEST_DATASOURCE_URL", matches = ".*_test.*")
class AdminInvitationMysqlTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired AdminInvitationService service;
    @MockBean NotificationService notifications;

    long member(String role) {
        String email = UUID.randomUUID() + "@test.local";
        jdbc.update(
                "INSERT INTO member(email, nickname, status, role) VALUES (?, ?, 'ACTIVE', ?)",
                email,
                "t" + UUID.randomUUID().toString().substring(0, 8),
                role);
        return jdbc.queryForObject("SELECT id FROM member WHERE email = ?", Long.class, email);
    }

    @Test
    void 동시초대와동시수락은_각각한번만기록된다() throws Exception {
        long actor = member("ADMIN");
        long target = member("USER");
        var executor = Executors.newFixedThreadPool(2);
        try {
            var start = new CountDownLatch(1);
            Callable<AdminInvitationService.Result> invite =
                    () -> {
                        start.await();
                        return service.invite(actor, target);
                    };
            var a = executor.submit(invite);
            var b = executor.submit(invite);
            start.countDown();
            long id = a.get(20, TimeUnit.SECONDS).invitationId();
            assertThat(b.get(20, TimeUnit.SECONDS).invitationId()).isEqualTo(id);
            var accepting = new CountDownLatch(1);
            Callable<AdminInvitationService.Result> accept =
                    () -> {
                        accepting.await();
                        return service.respond(target, id, true);
                    };
            var c = executor.submit(accept);
            var d = executor.submit(accept);
            accepting.countDown();
            assertThat(c.get(20, TimeUnit.SECONDS).status()).isEqualTo("ACCEPTED");
            assertThat(d.get(20, TimeUnit.SECONDS).status()).isEqualTo("ACCEPTED");
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT role FROM member WHERE id = ?", String.class, target))
                    .isEqualTo("ADMIN");
            for (String action : List.of("ADMIN_INVITE", "ADMIN_INVITE_ACCEPT", "ADMIN_GRANTED")) {
                assertThat(
                                jdbc.queryForObject(
                                        "SELECT COUNT(*) FROM admin_activity WHERE action = ? AND actor_id IN (?, ?)",
                                        Integer.class,
                                        action,
                                        actor,
                                        target))
                        .isEqualTo(1);
            }
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(20, TimeUnit.SECONDS);
            jdbc.update("DELETE FROM admin_activity WHERE actor_id IN (?, ?)", actor, target);
            jdbc.update("DELETE FROM admin_invitation WHERE invitee_id = ?", target);
            jdbc.update("DELETE FROM member WHERE id IN (?, ?)", actor, target);
        }
    }
}
