package com.gongbang.workspace.document.domain;

/**
 * DOC-02~04·DOC-10: 문서를 만들거나 이름을 바꾸거나 지우거나 본문을 저장했을 때 발행한다. 이름을 바꿨는데 같은 제목이면 발행하지 않는다.
 *
 * <p>트랜잭션 안에서 발행되므로 받는 쪽은 {@code @TransactionalEventListener(phase = AFTER_COMMIT)}로 받는다 ({@code
 * CalendarEventChangedEvent}와 같은 방식). {@code StompAfterCommitEventListener}가 팀 이벤트 {@code
 * DOCUMENT_*}로 바꿔 보낸다.
 */
public record DocumentChangedEvent(Long teamId, Long documentId, DocumentChangeType type) {}
