package com.gongbang.notification.service;

import com.gongbang.workspace.calendar.domain.CalendarEventChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class CalendarEventChangedListener {
    private final CalendarReminderService reminders;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(CalendarEventChangedEvent event) {
        try {
            reminders.refresh(event);
        } catch (RuntimeException e) {
            log.error("일정 알림 예약 실패: teamId={}, eventId={}", event.teamId(), event.eventId(), e);
        }
    }
}
