package com.gongbang.notification.service;

import com.gongbang.notification.domain.NotificationType;
import com.gongbang.notification.domain.ScheduledNotification;
import com.gongbang.team.service.TeamMemberService;
import com.gongbang.workspace.calendar.domain.CalendarEvent;
import com.gongbang.workspace.calendar.domain.CalendarEventChangedEvent;
import com.gongbang.workspace.calendar.service.CalendarService;
import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
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

    @PostConstruct
    void register() {
        scheduled.registerHandler("EVENT", this::send);
    }

    /** CAL-07 기본 정책: 시작 시각, 종일은 시작일 09:00. 확정 명세 수신 시 조정한다. */
    static LocalDateTime fireAt(CalendarEvent event) {
        return event.isAllDay()
                ? event.getStartAt().toLocalDate().atTime(9, 0)
                : event.getStartAt();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void refresh(CalendarEventChangedEvent changed) {
        // 이벤트 도착 순서가 뒤바뀌어도 DB의 최종 상태를 기준으로 예약한다.
        CalendarEvent event = calendar.findForReminder(changed.eventId()).orElse(null);
        if (event == null || event.isAuto()) {
            scheduled.cancel("EVENT", changed.eventId());
            return;
        }
        scheduled.schedule(
                NotificationType.EVENT_REMIND, "EVENT", changed.eventId(), fireAt(event));
    }

    /** fireDue의 트랜잭션 안에서 알림 저장과 SENT 전환을 함께 처리한다. */
    void send(ScheduledNotification item) {
        CalendarEvent event = calendar.findForReminder(item.getTargetId()).orElse(null);
        if (event == null || event.isAuto() || !fireAt(event).equals(item.getFireAt())) {
            return;
        }
        String message =
                event.isAllDay() ? "오늘 일정: " + event.getTitle() : "일정이 시작돼요: " + event.getTitle();
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
