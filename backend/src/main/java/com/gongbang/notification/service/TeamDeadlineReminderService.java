package com.gongbang.notification.service;

import com.gongbang.notification.domain.*;
import com.gongbang.notification.repository.ScheduledNotificationRepository;
import com.gongbang.team.service.TeamMemberService;
import com.gongbang.workspace.calendar.service.CalendarService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TeamDeadlineReminderService {
    private final CalendarService calendar;
    private final ScheduledNotificationService scheduled;
    private final ScheduledNotificationRepository repository;
    private final TeamMemberService members;
    private final NotificationService notifications;

    @PostConstruct
    void register() {
        scheduled.registerHandler("MILESTONE", this::send);
    }

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void reconcile() {
        for (var e : calendar.milestones()) {
            var at = e.getStartAt().toLocalDate().atTime(9, 0);
            if (repository.existsByTargetTypeAndTargetIdAndFireAtAndStatusNot(
                    "MILESTONE", e.getId(), at, ScheduledNotification.Status.CANCELED)) continue;
            scheduled.schedule(NotificationType.TEAM_DEADLINE, "MILESTONE", e.getId(), at);
        }
    }

    void send(ScheduledNotification item) {
        var e = calendar.findForReminder(item.getTargetId()).orElse(null);
        if (e == null
                || !e.isAuto()
                || !e.getStartAt().toLocalDate().atTime(9, 0).equals(item.getFireAt())) return;
        for (Long id : members.findActiveMemberIds(e.getTeamId()))
            notifications.send(
                    id,
                    NotificationType.TEAM_DEADLINE,
                    null,
                    "TEAM",
                    e.getTeamId(),
                    e.getTitle(),
                    "/workspaces/" + e.getTeamId() + "/calendar");
    }
}
