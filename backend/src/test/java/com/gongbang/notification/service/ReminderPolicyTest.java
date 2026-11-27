package com.gongbang.notification.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gongbang.member.repository.BookmarkRepository;
import com.gongbang.notification.domain.*;
import com.gongbang.notification.repository.ScheduledNotificationRepository;
import com.gongbang.team.service.TeamMemberService;
import com.gongbang.workspace.calendar.domain.CalendarEvent;
import com.gongbang.workspace.calendar.service.CalendarService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ReminderPolicyTest {
    @Test
    void 지난시각은_기존예약만_취소하고_즉시발송하지않는다() {
        var repo = mock(ScheduledNotificationRepository.class);
        var notifications = mock(NotificationService.class);
        var old =
                new ScheduledNotification(
                        NotificationType.EVENT_REMIND,
                        "EVENT",
                        1L,
                        LocalDateTime.now().plusDays(1));
        when(repo.findByTargetTypeAndTargetIdAndStatus(
                        "EVENT", 1L, ScheduledNotification.Status.PENDING))
                .thenReturn(List.of(old));
        new ScheduledNotificationService(repo, notifications, mock(BookmarkRepository.class))
                .schedule(
                        NotificationType.EVENT_REMIND,
                        "EVENT",
                        1L,
                        LocalDateTime.now().minusMinutes(1));
        assertThat(old.getStatus()).isEqualTo(ScheduledNotification.Status.CANCELED);
        verify(repo, never()).save(any());
        verifyNoInteractions(notifications);
    }

    @Test
    void 기존_시작시각예약은_전날예약으로_교체한다() {
        var repo = mock(ScheduledNotificationRepository.class);
        var calendar = mock(CalendarService.class);
        var scheduled = mock(ScheduledNotificationService.class);
        var start = LocalDateTime.of(2030, 1, 2, 19, 0);
        var event =
                CalendarEvent.create(1L, 2L, "회의", start, start.plusHours(1), false, "BLUE", null);
        when(repo.findByTargetTypeAndStatus("EVENT", ScheduledNotification.Status.PENDING))
                .thenReturn(
                        List.of(
                                new ScheduledNotification(
                                        NotificationType.EVENT_REMIND, "EVENT", 3L, start)));
        when(calendar.findForReminder(3L)).thenReturn(Optional.of(event));
        new CalendarReminderReconciliationService(
                        repo,
                        calendar,
                        scheduled,
                        mock(com.gongbang.team.service.TeamService.class))
                .reconcile();
        verify(scheduled)
                .schedule(NotificationType.EVENT_REMIND, "EVENT", 3L, start.minusHours(24));
    }

    @Test
    void 마감변경은_새예약으로_갱신하고_같은예약은_중복하지않는다() {
        var repo = mock(ScheduledNotificationRepository.class);
        var calendar = mock(CalendarService.class);
        var scheduled = mock(ScheduledNotificationService.class);
        var event = mock(CalendarEvent.class);
        var at = LocalDateTime.of(2030, 1, 2, 0, 0);
        when(event.getId()).thenReturn(3L);
        when(event.getStartAt()).thenReturn(at);
        when(calendar.milestones()).thenReturn(List.of(event));
        var service =
                new TeamDeadlineReminderService(
                        calendar,
                        scheduled,
                        repo,
                        mock(TeamMemberService.class),
                        mock(NotificationService.class),
                        mock(com.gongbang.team.service.TeamService.class));
        service.reconcile();
        verify(scheduled).schedule(NotificationType.TEAM_DEADLINE, "MILESTONE", 3L, at.withHour(9));
        when(repo.existsByTargetTypeAndTargetIdAndFireAtAndStatusNot(
                        "MILESTONE", 3L, at.withHour(9), ScheduledNotification.Status.CANCELED))
                .thenReturn(true);
        service.reconcile();
        verifyNoMoreInteractions(scheduled);
    }
}
