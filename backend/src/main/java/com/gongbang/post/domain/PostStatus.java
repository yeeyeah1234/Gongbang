package com.gongbang.post.domain;

/** 모집 상태 (DB 명세서 recruit_post.status). 모집완료는 되돌리지 않는다 (REC-007). */
public enum PostStatus {
    RECRUITING,
    COMPLETED,
    CLOSED
}
