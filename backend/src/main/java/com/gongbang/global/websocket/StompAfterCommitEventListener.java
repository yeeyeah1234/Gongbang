package com.gongbang.global.websocket;

import com.gongbang.workspace.calendar.domain.CalendarEventChangeType;
import com.gongbang.workspace.calendar.domain.CalendarEventChangedEvent;
import com.gongbang.workspace.notice.domain.NoticeChangeType;
import com.gongbang.workspace.notice.domain.NoticeChangedEvent;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 트랜잭션 커밋 뒤(`AFTER_COMMIT`)에만 팀·개인·공지·일정 STOMP 메시지를 브로커로 보낸다. */
@Component
@RequiredArgsConstructor
public class StompAfterCommitEventListener {

    private final SimpMessagingTemplate messagingTemplate;

    /** {@link TeamEventPublisher}로 발행된 팀 이벤트를 전송한다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTeamEvent(TeamEventMessage event) {
        messagingTemplate.convertAndSend(teamEventsDestination(event.teamId()), event);
    }

    /** {@link UserEventPublisher}로 발행된 개인 이벤트를 전송한다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserEvent(UserEventPublisher.UserEventDispatchedEvent event) {
        messagingTemplate.convertAndSend(
                memberEventsDestination(event.memberId()), event.message());
    }

    /** 공지 변경 이벤트를 팀 이벤트(`/sub/teams/{teamId}/events`)로 브리지한다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNoticeChanged(NoticeChangedEvent event) {
        TeamEventMessage message =
                new TeamEventMessage(
                        toNoticeEventType(event.type()),
                        event.teamId(),
                        event.noticeId(),
                        null,
                        LocalDateTime.now());
        messagingTemplate.convertAndSend(teamEventsDestination(event.teamId()), message);
    }

    /** 일정 변경 이벤트를 팀 이벤트(`/sub/teams/{teamId}/events`)로 브리지한다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCalendarEventChanged(CalendarEventChangedEvent event) {
        TeamEventMessage message =
                new TeamEventMessage(
                        toCalendarEventType(event.type()),
                        event.teamId(),
                        event.eventId(),
                        null,
                        LocalDateTime.now());
        messagingTemplate.convertAndSend(teamEventsDestination(event.teamId()), message);
    }

    static String teamEventsDestination(Long teamId) {
        return "/sub/teams/" + teamId + "/events";
    }

    static String memberEventsDestination(Long memberId) {
        return "/sub/members/" + memberId + "/events";
    }

    private static String toNoticeEventType(NoticeChangeType type) {
        return switch (type) {
            case CREATED -> "NOTICE_CREATED";
            case UPDATED -> "NOTICE_UPDATED";
            case DELETED -> "NOTICE_DELETED";
            case PINNED, UNPINNED -> "NOTICE_PIN_CHANGED";
        };
    }

    private static String toCalendarEventType(CalendarEventChangeType type) {
        return switch (type) {
            case CREATED -> "EVENT_CREATED";
            case UPDATED -> "EVENT_UPDATED";
            case DELETED -> "EVENT_DELETED";
        };
    }
}
