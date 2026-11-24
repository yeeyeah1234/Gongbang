package com.gongbang.workspace.whiteboard.domain;

/**
 * WB-01·WB-10: 보드를 만들거나 제목을 바꾸거나 지우거나 본문을 저장했을 때 발행한다. 제목이 그대로면 발행하지 않는다. actorId = 한 사람 (본문 저장은
 * lastEditorId).
 *
 * <p>트랜잭션 안에서 발행되므로 {@code StompAfterCommitEventListener}가 AFTER_COMMIT으로 받아 팀 이벤트 {@code
 * WHITEBOARD_*}로 바꿔 보낸다.
 */
public record WhiteboardChangedEvent(
        Long teamId, Long whiteboardId, WhiteboardChangeType type, Long actorId) {}
