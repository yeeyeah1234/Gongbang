package com.gongbang.post.domain;

/**
 * 모집글을 보는 사람의 상태 (PST-005 응답 myStatus, PST-006 버튼 분기). 11/10 결정.
 *
 * <p>ACCEPTED는 team_member 활성 행 기준, LEFT는 나간 기록만 있는 회원.
 */
public enum MyStatus {
    NONE,
    PENDING,
    ACCEPTED,
    REJECTED,
    AUTHOR,
    LEFT
}
