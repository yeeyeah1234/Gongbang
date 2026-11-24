package com.gongbang.application.controller;

import com.gongbang.application.dto.MyApplicationListItem;
import com.gongbang.application.service.MyApplicationListService;
import com.gongbang.auth.domain.LoginMember;
import com.gongbang.global.common.ApiResponse;
import com.gongbang.global.common.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MyApplicationListController {
    private final MyApplicationListService service;

    @GetMapping("/api/members/me/applications")
    public ApiResponse<PageResponse<MyApplicationListItem>> list(
            @AuthenticationPrincipal LoginMember me,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(service.list(me == null ? null : me.getMemberId(), page, size));
    }
}
