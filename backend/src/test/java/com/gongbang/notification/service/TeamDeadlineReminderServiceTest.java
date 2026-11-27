package com.gongbang.notification.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.gongbang.notification.domain.NotificationType;
import com.gongbang.notification.domain.ScheduledNotification;
import com.gongbang.notification.repository.ScheduledNotificationRepository;
import com.gongbang.team.service.TeamMemberService;
import com.gongbang.team.service.TeamService;
import com.gongbang.workspace.calendar.domain.CalendarEvent;
import com.gongbang.workspace.calendar.domain.CalendarEventAutoType;
import com.gongbang.workspace.calendar.service.CalendarService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TeamDeadlineReminderServiceTest {

    @Mock private CalendarService calendar;
    @Mock private ScheduledNotificationService scheduled;
    @Mock private ScheduledNotificationRepository repository;
    @Mock private TeamMemberService members;
    @Mock private NotificationService notifications;
    @Mock private TeamService teams;

    private TeamDeadlineReminderService service;

    @BeforeEach
    void setUp() {
        service =
                new TeamDeadlineReminderService(
                        calendar, scheduled, repository, members, notifications, teams);
    }

    @Test
    void 핸들러는_MILESTONE_D7과_MILESTONE_D1로_각각_등록한다() {
        service.register();

        verify(scheduled).registerHandler(eq("MILESTONE_D7"), any());
        verify(scheduled).registerHandler(eq("MILESTONE_D1"), any());
        verifyNoMoreInteractions(scheduled);
    }

    @Test
    void 일정_하나에_D7과_D1이_서로_다른_targetType으로_예약된다() {
        CalendarEvent deadline = autoDeadlineEvent(10L, 100L, LocalDateTime.of(2030, 5, 20, 0, 0));
        when(calendar.contestDeadlines()).thenReturn(List.of(deadline));

        service.reconcile();

        verify(scheduled)
                .schedule(
                        NotificationType.TEAM_DEADLINE,
                        "MILESTONE_D7",
                        10L,
                        LocalDateTime.of(2030, 5, 13, 9, 0));
        verify(scheduled)
                .schedule(
                        NotificationType.TEAM_DEADLINE,
                        "MILESTONE_D1",
                        10L,
                        LocalDateTime.of(2030, 5, 19, 9, 0));
        verifyNoMoreInteractions(scheduled);
    }

    @Test
    void 마감일이_바뀌면_D7과_D1_모두_새_시각으로_다시_예약된다() {
        CalendarEvent initial = autoDeadlineEvent(10L, 100L, LocalDateTime.of(2030, 5, 20, 0, 0));
        CalendarEvent updated = autoDeadlineEvent(10L, 100L, LocalDateTime.of(2030, 6, 10, 0, 0));
        when(calendar.contestDeadlines()).thenReturn(List.of(initial), List.of(updated));

        service.reconcile();
        service.reconcile();

        verify(scheduled)
                .schedule(
                        NotificationType.TEAM_DEADLINE,
                        "MILESTONE_D7",
                        10L,
                        LocalDateTime.of(2030, 5, 13, 9, 0));
        verify(scheduled)
                .schedule(
                        NotificationType.TEAM_DEADLINE,
                        "MILESTONE_D1",
                        10L,
                        LocalDateTime.of(2030, 5, 19, 9, 0));
        verify(scheduled)
                .schedule(
                        NotificationType.TEAM_DEADLINE,
                        "MILESTONE_D7",
                        10L,
                        LocalDateTime.of(2030, 6, 3, 9, 0));
        verify(scheduled)
                .schedule(
                        NotificationType.TEAM_DEADLINE,
                        "MILESTONE_D1",
                        10L,
                        LocalDateTime.of(2030, 6, 9, 9, 0));
        verifyNoMoreInteractions(scheduled);
    }

    @Test
    void 이미_같은_시각으로_예약되어_있으면_다시_예약하지_않는다() {
        LocalDateTime d7 = LocalDateTime.of(2030, 5, 13, 9, 0);
        LocalDateTime d1 = LocalDateTime.of(2030, 5, 19, 9, 0);
        CalendarEvent deadline = autoDeadlineEvent(10L, 100L, LocalDateTime.of(2030, 5, 20, 0, 0));
        when(calendar.contestDeadlines()).thenReturn(List.of(deadline));
        when(repository.existsByTargetTypeAndTargetIdAndFireAtAndStatusNot(
                        "MILESTONE_D7", 10L, d7, ScheduledNotification.Status.CANCELED))
                .thenReturn(true);
        when(repository.existsByTargetTypeAndTargetIdAndFireAtAndStatusNot(
                        "MILESTONE_D1", 10L, d1, ScheduledNotification.Status.CANCELED))
                .thenReturn(true);

        service.reconcile();

        verifyNoInteractions(scheduled);
    }

    @Test
    void 삭제된_팀은_예약과_발송을_하지_않는다() {
        CalendarEvent deadline = autoDeadlineEvent(10L, 100L, LocalDateTime.of(2030, 5, 20, 0, 0));
        when(calendar.contestDeadlines()).thenReturn(List.of(deadline));
        when(calendar.findForReminder(10L)).thenReturn(Optional.of(deadline));
        when(teams.isDeleted(100L)).thenReturn(true);

        service.reconcile();
        service.send(
                new ScheduledNotification(
                        NotificationType.TEAM_DEADLINE,
                        "MILESTONE_D7",
                        10L,
                        LocalDateTime.of(2030, 5, 13, 9, 0)));
        service.send(
                new ScheduledNotification(
                        NotificationType.TEAM_DEADLINE,
                        "MILESTONE_D1",
                        10L,
                        LocalDateTime.of(2030, 5, 19, 9, 0)));

        verify(scheduled).cancel("MILESTONE_D7", 10L);
        verify(scheduled).cancel("MILESTONE_D1", 10L);
        verify(scheduled, never()).schedule(any(), any(), any(), any());
        verifyNoInteractions(members, notifications);
    }

    @Test
    void fireAt이_마감일_계산값과_다르면_발송하지_않는다() {
        CalendarEvent deadline = autoDeadlineEvent(10L, 100L, LocalDateTime.of(2030, 5, 20, 0, 0));
        when(calendar.findForReminder(10L)).thenReturn(Optional.of(deadline));

        service.send(
                new ScheduledNotification(
                        NotificationType.TEAM_DEADLINE,
                        "MILESTONE_D7",
                        10L,
                        LocalDateTime.of(2030, 5, 14, 9, 0)));
        service.send(
                new ScheduledNotification(
                        NotificationType.TEAM_DEADLINE,
                        "MILESTONE_D1",
                        10L,
                        LocalDateTime.of(2030, 5, 18, 9, 0)));

        verifyNoInteractions(members, notifications);
    }

    @Test
    void 일정이_없거나_자동_일정이_아니면_발송하지_않는다() {
        LocalDateTime at = LocalDateTime.of(2030, 5, 20, 0, 0);
        CalendarEvent normal = CalendarEvent.create(100L, 1L, "일반 일정", at, at, true, "BLUE", null);
        when(calendar.findForReminder(10L)).thenReturn(Optional.empty());
        when(calendar.findForReminder(11L)).thenReturn(Optional.of(normal));

        service.send(
                new ScheduledNotification(
                        NotificationType.TEAM_DEADLINE,
                        "MILESTONE_D7",
                        10L,
                        LocalDateTime.of(2030, 5, 13, 9, 0)));
        service.send(
                new ScheduledNotification(
                        NotificationType.TEAM_DEADLINE,
                        "MILESTONE_D1",
                        11L,
                        LocalDateTime.of(2030, 5, 19, 9, 0)));

        verifyNoInteractions(members, notifications);
    }

    @Test
    void 발송_제목은_공모전_마감_D7과_D1이다() {
        CalendarEvent deadline = autoDeadlineEvent(10L, 100L, LocalDateTime.of(2030, 5, 20, 0, 0));
        when(calendar.findForReminder(10L)).thenReturn(Optional.of(deadline));
        when(members.findActiveMemberIds(100L)).thenReturn(List.of(1L, 2L));

        service.send(
                new ScheduledNotification(
                        NotificationType.TEAM_DEADLINE,
                        "MILESTONE_D7",
                        10L,
                        LocalDateTime.of(2030, 5, 13, 9, 0)));
        service.send(
                new ScheduledNotification(
                        NotificationType.TEAM_DEADLINE,
                        "MILESTONE_D1",
                        10L,
                        LocalDateTime.of(2030, 5, 19, 9, 0)));

        for (Long memberId : List.of(1L, 2L)) {
            verify(notifications)
                    .send(
                            memberId,
                            NotificationType.TEAM_DEADLINE,
                            null,
                            "TEAM",
                            100L,
                            "공모전 마감 D-7",
                            "/workspaces/100/calendar");
            verify(notifications)
                    .send(
                            memberId,
                            NotificationType.TEAM_DEADLINE,
                            null,
                            "TEAM",
                            100L,
                            "공모전 마감 D-1",
                            "/workspaces/100/calendar");
        }
        verifyNoMoreInteractions(notifications);
    }

    private CalendarEvent autoDeadlineEvent(Long id, Long teamId, LocalDateTime startAt) {
        CalendarEvent event =
                CalendarEvent.create(
                        teamId, null, "공모전 접수 마감", startAt, startAt, true, "RED", null);
        ReflectionTestUtils.setField(event, "id", id);
        ReflectionTestUtils.setField(event, "autoType", CalendarEventAutoType.CONTEST_DEADLINE);
        return event;
    }
}
