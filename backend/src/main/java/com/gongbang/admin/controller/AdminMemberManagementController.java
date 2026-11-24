package com.gongbang.admin.controller;

import com.gongbang.auth.domain.LoginMember;
import com.gongbang.global.common.ApiResponse;
import com.gongbang.member.service.AdminMemberManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/member-management")
@RequiredArgsConstructor
public class AdminMemberManagementController {
    private final AdminMemberManagementService service;

    @GetMapping
    public ApiResponse<Page<AdminMemberManagementService.Item>> list(
            @AuthenticationPrincipal LoginMember me,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "0") int page) {
        return ApiResponse.success(service.list(me.getMemberId(), keyword, status, page));
    }

    @PostMapping("/{id}/actions")
    public ApiResponse<AdminMemberManagementService.Item> act(
            @AuthenticationPrincipal LoginMember me,
            @PathVariable Long id,
            @RequestBody AdminMemberManagementService.Action body) {
        return ApiResponse.success(service.act(me.getMemberId(), id, body));
    }
}
