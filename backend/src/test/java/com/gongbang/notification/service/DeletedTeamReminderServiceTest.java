package com.gongbang.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.gongbang.notification.domain.NotificationType;
import com.gongbang.notification.domain.ScheduledNotification;
import com.gongbang.notification.repository.ScheduledNotificationRepository;
import com.gongbang.team.service.TeamDeletedEvent;
import com.gongbang.workspace.calendar.domain.CalendarEvent;
import com.gongbang.workspace.calendar.service.CalendarService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DeletedTeamReminderServiceTest {
    @Test
    void 삭제팀의_일정과마감예약만_취소하고_다른팀은_보존한다() {
        var repo = mock(ScheduledNotificationRepository.class);
        var calendar = mock(CalendarService.class);
        var at = LocalDateTime.now().plusDays(3);
        var own = new ScheduledNotification(NotificationType.EVENT_REMIND, "EVENT", 1L, at);
        var other = new ScheduledNotification(NotificationType.EVENT_REMIND, "EVENT", 2L, at);
        var milestone =
                new ScheduledNotification(NotificationType.TEAM_DEADLINE, "MILESTONE", 3L, at);
        when(repo.findByTargetTypeAndStatus("EVENT", ScheduledNotification.Status.PENDING))
                .thenReturn(List.of(own, other));
        when(repo.findByTargetTypeAndStatus("MILESTONE", ScheduledNotification.Status.PENDING))
                .thenReturn(List.of(milestone));
        var deletedTeamEvent =
                CalendarEvent.create(10L, 1L, "일정", at, at.plusHours(1), false, "BLUE", null);
        var otherEvent =
                CalendarEvent.create(20L, 1L, "일정", at, at.plusHours(1), false, "BLUE", null);
        when(calendar.findForReminder(1L)).thenReturn(Optional.of(deletedTeamEvent));
        when(calendar.findForReminder(2L)).thenReturn(Optional.of(otherEvent));
        when(calendar.findForReminder(3L)).thenReturn(Optional.of(deletedTeamEvent));
        var service = new DeletedTeamReminderService(repo, calendar);
        service.onDeleted(new TeamDeletedEvent(10L));
        service.onDeleted(new TeamDeletedEvent(10L));
        assertThat(own.getStatus()).isEqualTo(ScheduledNotification.Status.CANCELED);
        assertThat(milestone.getStatus()).isEqualTo(ScheduledNotification.Status.CANCELED);
        assertThat(other.getStatus()).isEqualTo(ScheduledNotification.Status.PENDING);
    }
}
