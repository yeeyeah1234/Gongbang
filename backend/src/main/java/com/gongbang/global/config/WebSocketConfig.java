package com.gongbang.global.config;

import com.gongbang.global.websocket.StompChannelInterceptor;
import jakarta.annotation.PreDestroy;
import java.util.Arrays;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/** STOMP over WebSocket 설정 (`AGENTS.md` §3). */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /** BunkerWeb 60초 유휴 연결 종료(REVERSE_PROXY_READ_TIMEOUT)를 피하기 위한 STOMP heartbeat 주기(ms). */
    static final long HEARTBEAT_INTERVAL_MS = 10_000L;

    private final ObjectProvider<StompChannelInterceptor> stompChannelInterceptorProvider;
    private final String[] allowedOrigins;
    private ThreadPoolTaskScheduler heartbeatTaskScheduler;

    public WebSocketConfig(
            ObjectProvider<StompChannelInterceptor> stompChannelInterceptorProvider,
            @Value(
                            "${app.cors.allowed-origins:http://localhost:5173,https://gongbang.apple-farm.online}")
                    String allowedOriginsRaw) {
        this.stompChannelInterceptorProvider = stompChannelInterceptorProvider;
        this.allowedOrigins =
                Arrays.stream(allowedOriginsRaw.split(","))
                        .map(String::trim)
                        .filter(origin -> !origin.isEmpty())
                        .toArray(String[]::new);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOrigins(allowedOrigins);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 별도 TaskScheduler @Bean으로 등록하면 @EnableScheduling 기본 스케줄러와 충돌할 수 있어
        // SimpleBroker 전용 스케줄러를 내부에서 생성하고 @PreDestroy로 정리한다.
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.initialize();
        this.heartbeatTaskScheduler = scheduler;

        registry.enableSimpleBroker("/sub")
                .setHeartbeatValue(new long[] {HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS})
                .setTaskScheduler(scheduler);
        registry.setApplicationDestinationPrefixes("/pub");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        stompChannelInterceptorProvider.ifAvailable(registration::interceptors);
    }

    @PreDestroy
    void shutdownHeartbeatScheduler() {
        if (heartbeatTaskScheduler != null) {
            heartbeatTaskScheduler.shutdown();
        }
    }
}
