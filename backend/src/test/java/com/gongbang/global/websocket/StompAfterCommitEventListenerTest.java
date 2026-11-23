package com.gongbang.global.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.gongbang.workspace.calendar.domain.CalendarEventChangeType;
import com.gongbang.workspace.calendar.domain.CalendarEventChangedEvent;
import com.gongbang.workspace.notice.domain.NoticeChangeType;
import com.gongbang.workspace.notice.domain.NoticeChangedEvent;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
class StompAfterCommitEventListenerTest {

    @Mock private SimpMessagingTemplate messagingTemplate;

    private StompAfterCommitEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new StompAfterCommitEventListener(messagingTemplate);
    }

    @Test
    @DisplayName("팀 이벤트는 /sub/teams/{teamId}/events로 전송한다")
    void 팀이벤트_전송() {
        TeamEventMessage event =
                new TeamEventMessage("MEMBER_JOINED", 5L, 20L, 20L, LocalDateTime.now());

        listener.onTeamEvent(event);

        verify(messagingTemplate).convertAndSend("/sub/teams/5/events", event);
    }

    @Test
    @DisplayName("개인 이벤트는 /sub/members/{memberId}/events로 전송한다")
    void 개인이벤트_전송() {
        UserEventMessage payload = new UserEventMessage("KICKED", "payload", LocalDateTime.now());
        listener.onUserEvent(new UserEventPublisher.UserEventDispatchedEvent(20L, payload));

        verify(messagingTemplate).convertAndSend("/sub/members/20/events", payload);
    }

    @Test
    @DisplayName("공지 변경 이벤트는 종류에 맞는 NOTICE_* 팀 이벤트로 브리지한다")
    void 공지이벤트_브리지() {
        listener.onNoticeChanged(new NoticeChangedEvent(3L, 100L, NoticeChangeType.CREATED));
        listener.onNoticeChanged(new NoticeChangedEvent(3L, 100L, NoticeChangeType.PINNED));

        ArgumentCaptor<TeamEventMessage> captor = ArgumentCaptor.forClass(TeamEventMessage.class);
        verify(messagingTemplate, org.mockito.Mockito.times(2))
                .convertAndSend(eq("/sub/teams/3/events"), captor.capture());

        assertThat(captor.getAllValues())
                .extracting(
                        TeamEventMessage::type,
                        TeamEventMessage::teamId,
                        TeamEventMessage::targetId)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("NOTICE_CREATED", 3L, 100L),
                        org.assertj.core.groups.Tuple.tuple("NOTICE_PIN_CHANGED", 3L, 100L));
    }

    @Test
    @DisplayName("일정 변경 이벤트는 EVENT_* 팀 이벤트로 브리지한다")
    void 일정이벤트_브리지() {
        listener.onCalendarEventChanged(
                new CalendarEventChangedEvent(
                        3L, 200L, CalendarEventChangeType.CREATED, LocalDateTime.now()));
        listener.onCalendarEventChanged(
                new CalendarEventChangedEvent(
                        3L, 200L, CalendarEventChangeType.DELETED, LocalDateTime.now()));

        ArgumentCaptor<TeamEventMessage> captor = ArgumentCaptor.forClass(TeamEventMessage.class);
        verify(messagingTemplate, org.mockito.Mockito.times(2))
                .convertAndSend(eq("/sub/teams/3/events"), captor.capture());

        assertThat(captor.getAllValues())
                .extracting(
                        TeamEventMessage::type,
                        TeamEventMessage::teamId,
                        TeamEventMessage::targetId)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("EVENT_CREATED", 3L, 200L),
                        org.assertj.core.groups.Tuple.tuple("EVENT_DELETED", 3L, 200L));
    }
}
