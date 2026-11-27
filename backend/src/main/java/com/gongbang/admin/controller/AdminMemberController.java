package com.gongbang.admin.controller;

import com.gongbang.auth.domain.LoginMember;
import com.gongbang.global.common.ApiResponse;
import com.gongbang.member.service.AdminMemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/members")
@RequiredArgsConstructor
public class AdminMemberController {
    private final AdminMemberService service;
    private final com.gongbang.member.service.AdminInvitationService invitations;

    @GetMapping
    public ApiResponse<AdminMemberService.Result> list(
            @AuthenticationPrincipal LoginMember me,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "0") int page) {
        return ApiResponse.success(service.list(me.getMemberId(), keyword, page));
    }

    @PostMapping("/{memberId}/admin-role")
    public ApiResponse<com.gongbang.member.service.AdminInvitationService.Result> grant(
            @AuthenticationPrincipal LoginMember me, @PathVariable Long memberId) {
        return ApiResponse.success(invitations.invite(me.getMemberId(), memberId));
    }
}
