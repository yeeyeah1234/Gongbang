package com.gongbang.workspace.notice.domain;

/**
 * NTC-01, NTC-06: 공지를 작성하면 하나 발행한다. 알림(정진우 notification, {@code NotificationType.NOTICE_NEW})이 받을
 * 예정이다.
 *
 * <p>작성 트랜잭션 안에서 발행되므로 받는 쪽은 {@code @TransactionalEventListener(phase = AFTER_COMMIT)}로 받는다. 그러면
 * 커밋된 뒤에만 실행되고(롤백되면 실행되지 않음), 받는 쪽 오류가 공지 저장을 되돌리지 않는다. AFTER_COMMIT 안에서 DB에 쓰려면 새 트랜잭션({@code
 * REQUIRES_NEW})이 필요하다. 작성자(authorMemberId)는 알림 대상에서 빼야 한다.
 */
public record NoticeCreatedEvent(Long teamId, Long noticeId, Long authorMemberId) {}
