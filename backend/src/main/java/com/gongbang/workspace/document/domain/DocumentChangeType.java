package com.gongbang.workspace.document.domain;

/** {@link DocumentChangedEvent}의 변경 종류. SAVED는 동기화 서버가 본문을 저장했을 때 (DOC-10). */
public enum DocumentChangeType {
    CREATED,
    RENAMED,
    DELETED,
    SAVED
}
