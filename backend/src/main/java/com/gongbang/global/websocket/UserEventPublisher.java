package com.gongbang.global.websocket;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 개인 실시간 이벤트 발행기 (`WS /sub/members/{memberId}/events`).
 *
 * <p>트랜잭션 안에서 부르면 {@link StompAfterCommitEventListener}가 {@code AFTER_COMMIT}에 실제로 전송한다.
 */
@Component
@RequiredArgsConstructor
public class UserEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    /** 개인 이벤트를 발행한다. 커밋 후 `/sub/members/{memberId}/events`로 전송된다. */
    public void publish(Long memberId, String type, Object payload) {
        eventPublisher.publishEvent(
                new UserEventDispatchedEvent(
                        memberId, new UserEventMessage(type, payload, LocalDateTime.now())));
    }

    /** {@code memberId} 라우팅 정보를 함께 담는 내부 스프링 이벤트. */
    public record UserEventDispatchedEvent(Long memberId, UserEventMessage message) {}
}
