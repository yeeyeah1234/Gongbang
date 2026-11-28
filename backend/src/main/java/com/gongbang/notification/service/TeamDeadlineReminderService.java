package com.gongbang.notification.service;

import com.gongbang.notification.domain.*;
import com.gongbang.notification.repository.ScheduledNotificationRepository;
import com.gongbang.team.service.TeamMemberService;
import com.gongbang.workspace.calendar.service.CalendarService;
import jakarta.annotation.PostConstruct;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TeamDeadlineReminderService {
    static final String TARGET_D7 = "MILESTONE_D7";
    static final String TARGET_D1 = "MILESTONE_D1";

    private record Milestone(String targetType, int daysBefore, String title) {}

    private static final List<Milestone> MILESTONES =
            List.of(
                    new Milestone(TARGET_D7, 7, "공모전 마감 D-7"),
                    new Milestone(TARGET_D1, 1, "공모전 마감 D-1"));

    private final CalendarService calendar;
    private final ScheduledNotificationService scheduled;
    private final ScheduledNotificationRepository repository;
    private final TeamMemberService members;
    private final NotificationService notifications;
    private final com.gongbang.team.service.TeamService teams;

    @PostConstruct
    void register() {
        for (var m : MILESTONES) {
            scheduled.registerHandler(m.targetType(), this::send);
        }
    }

    @Scheduled(fixedDelay = 60000)
    @Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void reconcile() {
        for (var e : calendar.contestDeadlines()) {
            if (teams.isDeleted(e.getTeamId())) {
                for (var m : MILESTONES) {
                    scheduled.cancel(m.targetType(), e.getId());
                }
                continue;
            }
            for (var m : MILESTONES) {
                var at = e.getStartAt().toLocalDate().minusDays(m.daysBefore()).atTime(9, 0);
                if (repository.existsByTargetTypeAndTargetIdAndFireAtAndStatusNot(
                        m.targetType(), e.getId(), at, ScheduledNotification.Status.CANCELED)) {
                    continue;
                }
                scheduled.schedule(NotificationType.TEAM_DEADLINE, m.targetType(), e.getId(), at);
            }
        }
    }

    void send(ScheduledNotification item) {
        var milestone = milestoneFor(item.getTargetType());
        if (milestone == null) return;
        var e = calendar.findForReminder(item.getTargetId()).orElse(null);
        if (e == null
                || !e.isAuto()
                || teams.isDeleted(e.getTeamId())
                || !e.getStartAt()
                        .toLocalDate()
                        .minusDays(milestone.daysBefore())
                        .atTime(9, 0)
                        .equals(item.getFireAt())) {
            return;
        }
        for (Long id : members.findActiveMemberIds(e.getTeamId())) {
            notifications.send(
                    id,
                    NotificationType.TEAM_DEADLINE,
                    null,
                    "TEAM",
                    e.getTeamId(),
                    milestone.title(),
                    "/workspaces/" + e.getTeamId() + "/calendar");
        }
    }

    private Milestone milestoneFor(String targetType) {
        for (var m : MILESTONES) {
            if (m.targetType().equals(targetType)) return m;
        }
        return null;
    }
}
