package com.gongbang.invitation.domain;

/** 팀 초대 상태 (`DB 명세서 team_invitation.status`). */
public enum InvitationStatus {
    PENDING,
    ACCEPTED,
    DECLINED,
    CANCELED
}
