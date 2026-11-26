package com.gongbang.invitation.controller;

import com.gongbang.auth.domain.LoginMember;
import com.gongbang.global.common.ApiResponse;
import com.gongbang.invitation.dto.InvitationAcceptResponse;
import com.gongbang.invitation.dto.InvitationCreateRequest;
import com.gongbang.invitation.dto.InvitationCreateResponse;
import com.gongbang.invitation.dto.RecruitingPostResponse;
import com.gongbang.invitation.service.InvitationCommandService;
import com.gongbang.invitation.service.InvitationQueryService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** NET-02 · INT-005 팀 초대 API 컨트롤러. */
@RestController
@RequiredArgsConstructor
public class InvitationController {

    private final InvitationQueryService invitationQueryService;
    private final InvitationCommandService invitationCommandService;

    /** 초대할 내 모집중 글 목록 (`GET /api/members/me/recruiting-posts`). */
    @GetMapping("/api/members/me/recruiting-posts")
    public ApiResponse<List<RecruitingPostResponse>> getMyRecruitingPosts(
            @AuthenticationPrincipal LoginMember loginMember) {
        return ApiResponse.success(
                invitationQueryService.getMyRecruitingPosts(loginMember.getMemberId()));
    }

    /** 팀 초대 보내기 (`POST /api/invitations`). */
    @PostMapping("/api/invitations")
    public ApiResponse<InvitationCreateResponse> createInvitation(
            @AuthenticationPrincipal LoginMember loginMember,
            @Valid @RequestBody InvitationCreateRequest request) {
        return ApiResponse.success(
                invitationCommandService.invite(loginMember.getMemberId(), request));
    }

    /** 팀 초대 수락 (`POST /api/invitations/{invitationId}/accept`). */
    @PostMapping("/api/invitations/{invitationId}/accept")
    public ApiResponse<InvitationAcceptResponse> acceptInvitation(
            @PathVariable Long invitationId, @AuthenticationPrincipal LoginMember loginMember) {
        return ApiResponse.success(
                invitationCommandService.accept(invitationId, loginMember.getMemberId()));
    }

    /** 팀 초대 거절 (`POST /api/invitations/{invitationId}/decline`). */
    @PostMapping("/api/invitations/{invitationId}/decline")
    public ApiResponse<Void> declineInvitation(
            @PathVariable Long invitationId, @AuthenticationPrincipal LoginMember loginMember) {
        invitationCommandService.decline(invitationId, loginMember.getMemberId());
        return ApiResponse.success();
    }
}
