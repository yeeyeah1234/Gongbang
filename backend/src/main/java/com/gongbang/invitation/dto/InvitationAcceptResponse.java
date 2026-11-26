package com.gongbang.invitation.dto;

/** NET-02 · INT-005 팀 초대 수락 응답 (`POST /api/invitations/{invitationId}/accept`). */
public record InvitationAcceptResponse(Long teamId) {}
