package com.gongbang.workspace.notice.domain;

/** {@link NoticeChangedEvent}의 변경 종류. */
public enum NoticeChangeType {
    CREATED,
    UPDATED,
    DELETED,
    PINNED,
    UNPINNED
}
