package com.gongbang.member.domain;

/** member.status (DB 명세서): ACTIVE / WITHDRAWN(탈퇴 후 30일 복원 대기) / DELETED(완전 삭제) */
public enum MemberStatus {
    ACTIVE,
    WITHDRAWN,
    DELETED
}
