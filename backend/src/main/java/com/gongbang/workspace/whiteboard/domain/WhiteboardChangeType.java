package com.gongbang.workspace.whiteboard.domain;

/**
 * {@link WhiteboardChangedEvent}의 변경 종류. API 명세서 팀 이벤트 WHITEBOARD_CREATED·_UPDATED·_DELETED와 맞춘다.
 *
 * <p>UPDATED는 제목 변경과 동기화 서버의 본문 저장(WB-10) 둘 다다. 목록의 마지막 수정자·수정 시각이 바뀌기 때문이다.
 */
public enum WhiteboardChangeType {
    CREATED,
    UPDATED,
    DELETED
}
