package com.gongbang.application.domain;

/** 거절 방식 (DB 명세서 application.reject_type). AUTO는 모집완료 때 남은 대기 지원 자동 거절 (REC-006 ④). */
public enum RejectType {
    MANUAL,
    AUTO
}
