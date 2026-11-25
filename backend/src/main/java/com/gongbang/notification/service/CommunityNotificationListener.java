package com.gongbang.notification.service;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
public class CommunityNotificationListener {
    private final CommunityNotificationService service;
    private final ExecutorService executor;

    @Autowired
    public CommunityNotificationListener(CommunityNotificationService service) {
        this(
                service,
                new ThreadPoolExecutor(
                        1,
                        2,
                        30,
                        TimeUnit.SECONDS,
                        new ArrayBlockingQueue<>(100),
                        runnable -> {
                            Thread thread = new Thread(runnable, "community-notification");
                            thread.setDaemon(true);
                            return thread;
                        },
                        new ThreadPoolExecutor.AbortPolicy()));
    }

    CommunityNotificationListener(CommunityNotificationService service, ExecutorService executor) {
        this.service = service;
        this.executor = executor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void created(CommunityPostCreatedEvent event) {
        try {
            executor.execute(
                    () -> {
                        try {
                            service.deliver(event);
                        } catch (RuntimeException failure) {
                            log.error(
                                    "Community notification batch failed: postId={}, errorType={}",
                                    event.postId(),
                                    failure.getClass().getSimpleName());
                        }
                    });
        } catch (RejectedExecutionException failure) {
            // 커밋된 글의 응답을 실패로 바꾸지 않는다. 메모리 큐 유실은 운영 로그로 추적한다.
            log.error("Community notification queue rejected: postId={}", event.postId());
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(20, TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException failure) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
