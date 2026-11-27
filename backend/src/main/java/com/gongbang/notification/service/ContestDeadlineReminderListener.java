package com.gongbang.notification.service;

import com.gongbang.contest.domain.ContestDeadlineChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** CAL-06의 자동 일정 갱신(Order 0)이 커밋된 뒤 최종 DB 날짜로 D-7/D-1 예약을 갱신한다. */
@Component
@RequiredArgsConstructor
@Slf4j
public class ContestDeadlineReminderListener {
    private final TeamDeadlineReminderService reminders;

    @Order(1)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ContestDeadlineChangedEvent event) {
        try {
            reminders.reconcile();
        } catch (RuntimeException failure) {
            log.error(
                    "마감 알림 재예약 실패: contestId={}, errorType={}",
                    event.contestId(),
                    failure.getClass().getSimpleName());
        }
    }
}
