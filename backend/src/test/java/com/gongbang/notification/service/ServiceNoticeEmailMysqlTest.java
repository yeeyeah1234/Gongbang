package com.gongbang.notification.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gongbang.auth.domain.LoginMember;
import com.gongbang.member.domain.*;
import com.gongbang.member.service.MemberService;
import com.gongbang.notification.domain.NotificationType;
import com.gongbang.notification.dto.ServiceNoticeRequest;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 실제 테스트 DB의 행 잠금으로 동시 발송 요청 중복을 검증한다. 실제 SMTP는 사용하지 않는다. */
@SpringBootTest(
        properties = {
            "spring.autoconfigure.exclude=",
            "spring.datasource.url=${TEST_DATASOURCE_URL:}",
            "spring.datasource.username=${TEST_DATASOURCE_USERNAME:root}",
            "spring.datasource.password=${TEST_DATASOURCE_PASSWORD:}",
            "app.profile-storage=local"
        })
@EnabledIfEnvironmentVariable(named = "TEST_DATASOURCE_URL", matches = ".*_test.*")
class ServiceNoticeEmailMysqlTest {
    @Autowired ServiceNoticeService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @MockBean MemberService members;
    @MockBean EmailOutboxService emails;
    @MockBean NotificationService notifications;

    @Test
    void 동시에_같은공지_이메일을_요청해도_한번만_등록한다() throws Exception {
        var tx = new TransactionTemplate(transactions);
        Long actor =
                tx.execute(
                        s -> {
                            jdbc.update(
                                    "INSERT INTO member (status, role) VALUES ('ACTIVE', 'ADMIN')");
                            return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
                        });
        Long notice = null;
        var executor = Executors.newFixedThreadPool(2);
        try {
            notice =
                    tx.execute(
                            s -> {
                                jdbc.update(
                                        "INSERT INTO service_notice (author_id,title,content) VALUES (?, 'policy-test', 'body')",
                                        actor);
                                return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
                            });
            var admin = Member.createEmailMember("policy-test@example.com", "hash", "관리자");
            admin.grantAdmin();
            when(members.getActiveMember(actor)).thenReturn(admin);
            when(members.findActiveMemberIds()).thenReturn(List.of(actor));
            when(members.findActiveMember(actor)).thenReturn(Optional.of(admin));
            final Long id = notice;
            var start = new CountDownLatch(1);
            Callable<Long> request =
                    () -> {
                        if (!start.await(5, TimeUnit.SECONDS))
                            throw new IllegalStateException("start timeout");
                        return service.update(
                                new LoginMember(actor, MemberRole.ADMIN),
                                id,
                                new ServiceNoticeRequest("policy-test", "body", true, true));
                    };
            var a = executor.submit(request);
            var b = executor.submit(request);
            start.countDown();
            a.get(15, TimeUnit.SECONDS);
            b.get(15, TimeUnit.SECONDS);
            verify(emails, times(1))
                    .enqueueNotification(
                            eq(admin),
                            eq(NotificationType.POLICY_NOTICE),
                            isNull(),
                            eq("policy-test"),
                            eq("/service-notices/" + id),
                            any());
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(20, TimeUnit.SECONDS);
            if (notice != null) jdbc.update("DELETE FROM service_notice WHERE id=?", notice);
            jdbc.update("DELETE FROM member WHERE id=?", actor);
        }
    }
}
