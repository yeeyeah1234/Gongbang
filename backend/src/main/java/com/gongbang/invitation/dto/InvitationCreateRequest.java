package com.gongbang.invitation.dto;

import jakarta.validation.constraints.NotNull;

/** NET-02 팀 초대 보내기 요청 (`POST /api/invitations`). */
public record InvitationCreateRequest(
        @NotNull(message = "초대할 모집글을 선택해 주세요") Long postId,
        @NotNull(message = "초대할 회원을 지정해 주세요") Long inviteeId) {}
