package com.gongbang.notification.service;

import com.gongbang.notification.domain.NotificationType;
import com.gongbang.notification.domain.ScheduledNotification;
import com.gongbang.notification.repository.ScheduledNotificationRepository;
import com.gongbang.workspace.calendar.service.CalendarService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 기존 시작 시각 정책으로 저장된 PENDING 예약도 배포 후 새 정책으로 교체한다. */
@Service
@RequiredArgsConstructor
public class CalendarReminderReconciliationService {
    private final ScheduledNotificationRepository repository;
    private final CalendarService calendar;
    private final ScheduledNotificationService scheduled;
    private final com.gongbang.team.service.TeamService teams;

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void reconcile() {
        for (var pending :
                repository.findByTargetTypeAndStatus(
                        "EVENT", ScheduledNotification.Status.PENDING)) {
            if (pending.getStatus() != ScheduledNotification.Status.PENDING) continue;
            var event = calendar.findForReminder(pending.getTargetId()).orElse(null);
            if (event == null || event.isAuto() || teams.isDeleted(event.getTeamId())) {
                scheduled.cancel("EVENT", pending.getTargetId());
            } else if (!CalendarReminderService.fireAt(event).equals(pending.getFireAt())) {
                scheduled.schedule(
                        NotificationType.EVENT_REMIND,
                        "EVENT",
                        pending.getTargetId(),
                        CalendarReminderService.fireAt(event));
            }
        }
    }
}
