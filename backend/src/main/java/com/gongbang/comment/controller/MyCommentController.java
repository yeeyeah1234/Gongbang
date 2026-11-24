package com.gongbang.comment.controller;

import com.gongbang.auth.domain.LoginMember;
import com.gongbang.comment.dto.MyCommentItem;
import com.gongbang.comment.service.MyCommentService;
import com.gongbang.global.common.ApiResponse;
import com.gongbang.global.common.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MyCommentController {
    private final MyCommentService service;

    @GetMapping("/api/members/me/comments")
    public ApiResponse<PageResponse<MyCommentItem>> list(
            @AuthenticationPrincipal LoginMember me,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(service.list(me == null ? null : me.getMemberId(), page, size));
    }
}
