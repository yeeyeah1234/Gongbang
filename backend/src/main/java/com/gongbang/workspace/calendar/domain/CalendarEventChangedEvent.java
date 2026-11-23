package com.gongbang.workspace.calendar.domain;

import java.time.LocalDateTime;

/**
 * CAL-03~05: 일반 일정을 만들거나 고치거나 지울 때 발행한다. 일정 알림(CAL-07, 정진우)과 화면 갱신 신호(STOMP, 김건우)가 받을 예정이고 지금은 받는
 * 쪽이 없다.
 *
 * <p>startAt은 생성·수정이면 저장된 새 시작 시각, 삭제면 지운 일정의 시작 시각이다. 수정했는데 바뀐 값이 없으면 발행하지 않는다.
 *
 * <p>트랜잭션 안에서 발행되므로 받는 쪽은 {@code @TransactionalEventListener(phase = AFTER_COMMIT)}로 받는다 ({@code
 * NoticeCreatedEvent}와 같은 방식). AFTER_COMMIT 안에서 DB에 쓰려면 새 트랜잭션({@code REQUIRES_NEW})이 필요하다.
 */
public record CalendarEventChangedEvent(
        Long teamId, Long eventId, CalendarEventChangeType type, LocalDateTime startAt) {}
