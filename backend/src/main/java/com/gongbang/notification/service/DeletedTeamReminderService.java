package com.gongbang.notification.service;

import com.gongbang.notification.domain.ScheduledNotification;
import com.gongbang.notification.repository.ScheduledNotificationRepository;
import com.gongbang.team.service.TeamDeletedEvent;
import com.gongbang.workspace.calendar.service.CalendarService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** WS-08 삭제 커밋 이후 CAL-07 및 팀 마감 대기 예약을 취소한다. */
@Service
@RequiredArgsConstructor
public class DeletedTeamReminderService {
    private final ScheduledNotificationRepository repository;
    private final CalendarService calendar;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onDeleted(TeamDeletedEvent event) {
        for (String type : java.util.List.of("EVENT", "MILESTONE")) {
            for (var pending :
                    repository.findByTargetTypeAndStatus(
                            type, ScheduledNotification.Status.PENDING)) {
                var target = calendar.findForReminder(pending.getTargetId()).orElse(null);
                if (target == null || event.teamId().equals(target.getTeamId())) pending.cancel();
            }
        }
    }
}
