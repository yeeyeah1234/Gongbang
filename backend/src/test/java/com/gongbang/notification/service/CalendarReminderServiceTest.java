package com.gongbang.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.gongbang.notification.domain.NotificationType;
import com.gongbang.notification.domain.ScheduledNotification;
import com.gongbang.team.service.TeamMemberService;
import com.gongbang.workspace.calendar.domain.*;
import com.gongbang.workspace.calendar.service.CalendarService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CalendarReminderServiceTest {
    private final ScheduledNotificationService scheduled = mock(ScheduledNotificationService.class);
    private final CalendarService calendar = mock(CalendarService.class);
    private final TeamMemberService members = mock(TeamMemberService.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final CalendarReminderService service =
            new CalendarReminderService(scheduled, calendar, members, notifications);
    private final LocalDateTime start = LocalDateTime.of(2030, 1, 2, 14, 0);

    private CalendarEvent event(boolean allDay) {
        return CalendarEvent.create(1L, 2L, "회의", start, start.plusHours(1), allDay, "BLUE", null);
    }

    @Test
    void 생성과수정은_최신일정으로_예약한다() {
        when(calendar.findForReminder(3L)).thenReturn(Optional.of(event(false)));
        service.refresh(
                new CalendarEventChangedEvent(
                        1L, 3L, CalendarEventChangeType.UPDATED, start.minusHours(1)));
        verify(scheduled).schedule(NotificationType.EVENT_REMIND, "EVENT", 3L, start);
        assertThat(CalendarReminderService.fireAt(event(true)))
                .isEqualTo(start.toLocalDate().atTime(9, 0));
    }

    @Test
    void 삭제된일정은_예약을취소한다() {
        when(calendar.findForReminder(3L)).thenReturn(Optional.empty());
        service.refresh(
                new CalendarEventChangedEvent(1L, 3L, CalendarEventChangeType.DELETED, start));
        verify(scheduled).cancel("EVENT", 3L);
    }

    @Test
    void 현재팀원에게_작성자도포함하여_발송한다() {
        when(calendar.findForReminder(3L)).thenReturn(Optional.of(event(false)));
        when(members.findActiveMemberIds(1L)).thenReturn(List.of(2L, 4L));
        service.send(new ScheduledNotification(NotificationType.EVENT_REMIND, "EVENT", 3L, start));
        for (long receiver : new long[] {2L, 4L}) {
            verify(notifications)
                    .send(
                            receiver,
                            NotificationType.EVENT_REMIND,
                            null,
                            "EVENT",
                            3L,
                            "일정이 시작돼요: 회의",
                            "/workspaces/1/calendar");
        }
    }

    @Test
    void 변경전예약은_발송하지않는다() {
        when(calendar.findForReminder(3L)).thenReturn(Optional.of(event(false)));
        service.send(
                new ScheduledNotification(
                        NotificationType.EVENT_REMIND, "EVENT", 3L, start.minusHours(1)));
        verifyNoInteractions(notifications);
    }
}
