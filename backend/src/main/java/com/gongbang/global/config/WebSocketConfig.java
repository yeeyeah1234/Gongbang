package com.gongbang.global.config;

import com.gongbang.global.websocket.StompChannelInterceptor;
import java.util.Arrays;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/** STOMP over WebSocket 설정 (`AGENTS.md` §3). */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final ObjectProvider<StompChannelInterceptor> stompChannelInterceptorProvider;
    private final String[] allowedOrigins;

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
        registry.enableSimpleBroker("/sub");
        registry.setApplicationDestinationPrefixes("/pub");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        stompChannelInterceptorProvider.ifAvailable(registration::interceptors);
    }
}
