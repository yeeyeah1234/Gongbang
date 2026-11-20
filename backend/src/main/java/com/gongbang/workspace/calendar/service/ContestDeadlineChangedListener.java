package com.gongbang.workspace.calendar.service;

import com.gongbang.contest.domain.ContestDeadlineChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * CRW-01 ⑦, CAL-06: 크롤러 배치가 커밋된 뒤 마감일이 바뀐 공모전의 팀 자동 일정을 맞춘다.
 *
 * <p>트랜잭션은 여기서 열지 않고 {@link CalendarService#upsertAutoEventsByContest}(REQUIRES_NEW)가 연다. 여기에
 * {@code @Transactional}을 두면 try/catch가 트랜잭션 안쪽이 되어, 서비스 예외로 rollback-only가 된 트랜잭션을 커밋할 때 {@code
 * UnexpectedRollbackException}이 catch 밖으로 나간다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContestDeadlineChangedListener {

    private final CalendarService calendarService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ContestDeadlineChangedEvent event) {
        try {
            calendarService.upsertAutoEventsByContest(event.contestId());
        } catch (RuntimeException e) {
            // 배치는 이미 커밋됐다. 실패해도 배치에 영향을 주지 않도록 남기기만 한다.
            log.warn(
                    "공모전 마감 자동 일정 갱신 실패: contestId={}, cause={}: {}",
                    event.contestId(),
                    e.getClass().getSimpleName(),
                    e.getMessage());
        }
    }
}
