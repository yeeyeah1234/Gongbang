package com.gongbang.admin.controller;

import com.gongbang.admin.service.AdminActivityQueryService;
import com.gongbang.auth.domain.LoginMember;
import com.gongbang.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/activity-logs")
public class AdminActivityController {
    private final AdminActivityQueryService service;

    @GetMapping
    public ApiResponse<?> list(
            @AuthenticationPrincipal LoginMember me,
            @RequestParam(defaultValue = "") String action,
            @RequestParam(defaultValue = "0") int page) {
        return ApiResponse.success(service.list(me.getMemberId(), action, page));
    }

    @GetMapping("/{id}/target")
    public ApiResponse<?> target(@AuthenticationPrincipal LoginMember me, @PathVariable Long id) {
        return ApiResponse.success(service.target(me.getMemberId(), id));
    }
}
