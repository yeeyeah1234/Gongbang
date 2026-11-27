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
    private final com.gongbang.team.service.TeamService teams;

    @PostConstruct
    void register() {
        scheduled.registerHandler("MILESTONE", this::send);
    }

    @Scheduled(fixedDelay = 60000)
    @Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void reconcile() {
        for (var e : calendar.milestones()) {
            if (teams.isDeleted(e.getTeamId())) {
                scheduled.cancel("MILESTONE", e.getId());
                continue;
            }
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
                || teams.isDeleted(e.getTeamId())
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
