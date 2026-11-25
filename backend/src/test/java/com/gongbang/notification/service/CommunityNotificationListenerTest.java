package com.gongbang.notification.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionalEventListenerFactory;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class CommunityNotificationListenerTest {
    @Test
    void 커밋후에만_큐에_넣고_롤백하면_알리지않는다() {
        var service = mock(CommunityNotificationService.class);
        var executor = mock(ExecutorService.class);
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(TransactionalEventListenerFactory.class);
            context.registerBean(
                    CommunityNotificationListener.class,
                    () -> new CommunityNotificationListener(service, executor));
            context.refresh();
            var tx = new TransactionTemplate(new TestTransactions());
            var event = new CommunityPostCreatedEvent(1L, 8L, "제목");
            tx.executeWithoutResult(
                    status -> {
                        context.publishEvent(event);
                        verify(executor, never()).execute(any());
                        status.setRollbackOnly();
                    });
            verify(executor, never()).execute(any());
            tx.executeWithoutResult(
                    status -> {
                        context.publishEvent(event);
                        verify(executor, never()).execute(any());
                    });
            var task = org.mockito.ArgumentCaptor.forClass(Runnable.class);
            verify(executor).execute(task.capture());
            verifyNoInteractions(service);
            task.getValue().run();
            verify(service).deliver(event);
        }
    }

    @Test
    void 큐가가득차도_커밋응답을_실패시키지않는다() {
        var executor = mock(ExecutorService.class);
        doThrow(new RejectedExecutionException()).when(executor).execute(any());
        var listener =
                new CommunityNotificationListener(
                        mock(CommunityNotificationService.class), executor);
        assertThatCode(() -> listener.created(new CommunityPostCreatedEvent(1L, 8L, "제목")))
                .doesNotThrowAnyException();
    }

    static class TestTransactions extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {}

        @Override
        protected void doCommit(DefaultTransactionStatus status) {}

        @Override
        protected void doRollback(DefaultTransactionStatus status) {}
    }
}
