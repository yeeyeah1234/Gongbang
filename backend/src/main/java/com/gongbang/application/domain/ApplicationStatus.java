package com.gongbang.application.domain;

/** 지원 상태 (DB 명세서 application.status). CLOSED는 모집글 삭제로 끝난 지원 (PST-004 ④). */
public enum ApplicationStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    CLOSED
}
