package com.gongbang.global.websocket;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 팀 실시간 이벤트 발행기 (`WS /sub/teams/{teamId}/events`).
 *
 * <p>트랜잭션 안에서 부르면 {@link StompAfterCommitEventListener}가 {@code AFTER_COMMIT}에 실제로 전송한다.
 */
@Component
@RequiredArgsConstructor
public class TeamEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    /** 팀 이벤트를 발행한다. 커밋 후 `/sub/teams/{teamId}/events`로 전송된다. */
    public void publish(Long teamId, String type, Long targetId, Long actorId) {
        eventPublisher.publishEvent(
                new TeamEventMessage(type, teamId, targetId, actorId, LocalDateTime.now()));
    }
}
