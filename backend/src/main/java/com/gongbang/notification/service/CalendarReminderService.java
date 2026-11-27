package com.gongbang.notification.service;

import com.gongbang.notification.domain.NotificationType;
import com.gongbang.notification.domain.ScheduledNotification;
import com.gongbang.team.service.TeamMemberService;
import com.gongbang.workspace.calendar.domain.CalendarEvent;
import com.gongbang.workspace.calendar.domain.CalendarEventChangedEvent;
import com.gongbang.workspace.calendar.service.CalendarService;
import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CalendarReminderService {
    private final ScheduledNotificationService scheduled;
    private final CalendarService calendar;
    private final TeamMemberService members;
    private final NotificationService notifications;
    private final com.gongbang.team.service.TeamService teams;

    @PostConstruct
    void register() {
        scheduled.registerHandler("EVENT", this::send);
    }

    /** CAL-07: 시간 일정은 24시간 전, 종일 일정은 전날 오전 9시(한국 시간). */
    static LocalDateTime fireAt(CalendarEvent event) {
        return event.isAllDay()
                ? event.getStartAt().toLocalDate().minusDays(1).atTime(9, 0)
                : event.getStartAt().minusHours(24);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void refresh(CalendarEventChangedEvent changed) {
        // 이벤트 도착 순서가 뒤바뀌어도 DB의 최종 상태를 기준으로 예약한다.
        CalendarEvent event = calendar.findForReminder(changed.eventId()).orElse(null);
        if (event == null || event.isAuto() || teams.isDeleted(event.getTeamId())) {
            scheduled.cancel("EVENT", changed.eventId());
            return;
        }
        scheduled.schedule(
                NotificationType.EVENT_REMIND, "EVENT", changed.eventId(), fireAt(event));
    }

    /** fireDue의 트랜잭션 안에서 알림 저장과 SENT 전환을 함께 처리한다. */
    void send(ScheduledNotification item) {
        CalendarEvent event = calendar.findForReminder(item.getTargetId()).orElse(null);
        if (event == null
                || event.isAuto()
                || teams.isDeleted(event.getTeamId())
                || !fireAt(event).equals(item.getFireAt())) {
            return;
        }
        String message =
                "내일 "
                        + (event.isAllDay()
                                ? ""
                                : event.getStartAt().format(DateTimeFormatter.ofPattern("HH:mm"))
                                        + " ")
                        + "‘"
                        + event.getTitle()
                        + "’ 일정이 있어요";
        for (Long receiver : members.findActiveMemberIds(event.getTeamId())) {
            notifications.send(
                    receiver,
                    NotificationType.EVENT_REMIND,
                    null,
                    "EVENT",
                    item.getTargetId(),
                    message,
                    "/workspaces/" + event.getTeamId() + "/calendar");
        }
    }
}
