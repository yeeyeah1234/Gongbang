package com.gongbang.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gongbang.global.websocket.StompChannelInterceptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.config.SimpleBrokerRegistration;
import org.springframework.scheduling.TaskScheduler;

class WebSocketConfigTest {

    @Test
    @DisplayName("SimpleBroker에 10초 heartbeat와 전용 TaskScheduler를 설정하고 종료 시 스케줄러를 정리한다")
    @SuppressWarnings("unchecked")
    void simpleBroker_heartbeat_설정() {
        ObjectProvider<StompChannelInterceptor> interceptorProvider = mock(ObjectProvider.class);
        WebSocketConfig config = new WebSocketConfig(interceptorProvider, "http://localhost:5173");

        MessageBrokerRegistry registry = mock(MessageBrokerRegistry.class);
        SimpleBrokerRegistration registration = mock(SimpleBrokerRegistration.class);
        when(registry.enableSimpleBroker("/sub")).thenReturn(registration);
        when(registration.setHeartbeatValue(new long[] {10_000L, 10_000L}))
                .thenReturn(registration);
        when(registration.setTaskScheduler(org.mockito.ArgumentMatchers.any(TaskScheduler.class)))
                .thenReturn(registration);

        try {
            config.configureMessageBroker(registry);

            verify(registry).enableSimpleBroker("/sub");
            verify(registration).setHeartbeatValue(new long[] {10_000L, 10_000L});
            ArgumentCaptor<TaskScheduler> schedulerCaptor =
                    ArgumentCaptor.forClass(TaskScheduler.class);
            verify(registration).setTaskScheduler(schedulerCaptor.capture());
            assertThat(schedulerCaptor.getValue()).isNotNull();
            verify(registry).setApplicationDestinationPrefixes("/pub");
        } finally {
            config.shutdownHeartbeatScheduler();
        }
    }
}
