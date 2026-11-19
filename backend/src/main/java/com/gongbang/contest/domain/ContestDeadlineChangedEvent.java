package com.gongbang.contest.domain;

import java.time.LocalDate;

/**
 * CRW-01 ⑦, CAL-06: 크롤러 배치에서 이미 있던 공모전의 마감일(end_date)이 바뀌면 공모전마다 하나씩 발행한다. 새로 추가된 공모전은 연결된 팀이 없으므로
 * 발행하지 않는다.
 *
 * <p>배치 트랜잭션 안에서 발행되므로 받는 쪽은 {@code @TransactionalEventListener(phase = AFTER_COMMIT)}로 받는다. 그러면
 * 배치가 커밋된 뒤에만 실행되고(롤백되면 실행되지 않음), 받는 쪽 오류가 배치 저장을 되돌리지 않는다. AFTER_COMMIT 안에서 DB에 쓰려면 새 트랜잭션({@code
 * REQUIRES_NEW})이 필요하다.
 */
public record ContestDeadlineChangedEvent(Long contestId, LocalDate newEndDate) {}
