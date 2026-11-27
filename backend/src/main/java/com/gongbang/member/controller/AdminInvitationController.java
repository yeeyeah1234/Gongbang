package com.gongbang.member.controller;

import com.gongbang.auth.domain.LoginMember;
import com.gongbang.global.common.ApiResponse;
import com.gongbang.member.service.AdminInvitationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin-invitations")
public class AdminInvitationController {
    private final AdminInvitationService service;

    @PostMapping("/{id}/accept")
    public ApiResponse<AdminInvitationService.Result> accept(
            @AuthenticationPrincipal LoginMember me, @PathVariable Long id) {
        return ApiResponse.success(service.respond(me.getMemberId(), id, true));
    }

    @PostMapping("/{id}/decline")
    public ApiResponse<AdminInvitationService.Result> decline(
            @AuthenticationPrincipal LoginMember me, @PathVariable Long id) {
        return ApiResponse.success(service.respond(me.getMemberId(), id, false));
    }
}
