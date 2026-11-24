package com.gongbang.admin.controller;

import com.gongbang.auth.domain.LoginMember;
import com.gongbang.contest.service.*;
import com.gongbang.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class AdminContestController {
    private final AdminContestService contests;
    private final AdminCrawlerService crawler;

    @GetMapping("/contests")
    public ApiResponse<?> list(
            @AuthenticationPrincipal LoginMember me,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "0") int page) {
        return ApiResponse.success(contests.list(me.getMemberId(), keyword, page));
    }

    @GetMapping("/contests/{id}/teams")
    public ApiResponse<?> teams(@AuthenticationPrincipal LoginMember me, @PathVariable Long id) {
        return ApiResponse.success(contests.teams(me.getMemberId(), id));
    }

    @PatchMapping("/contests/{id}")
    public ApiResponse<?> update(
            @AuthenticationPrincipal LoginMember me,
            @PathVariable Long id,
            @RequestBody AdminContestService.Update body) {
        return ApiResponse.success(contests.update(me.getMemberId(), id, body));
    }

    @GetMapping("/categories")
    public ApiResponse<?> categories(@AuthenticationPrincipal LoginMember me) {
        return ApiResponse.success(contests.categories(me.getMemberId()));
    }

    @PatchMapping("/categories/{id}")
    public ApiResponse<?> category(
            @AuthenticationPrincipal LoginMember me,
            @PathVariable Long id,
            @RequestBody AdminContestService.CategoryUpdate body) {
        contests.category(me.getMemberId(), id, body);
        return ApiResponse.success();
    }

    @GetMapping("/crawler-jobs")
    public ApiResponse<?> jobs(@AuthenticationPrincipal LoginMember me) {
        return ApiResponse.success(crawler.list(me.getMemberId()));
    }

    @PostMapping("/crawler-jobs")
    public ApiResponse<?> run(@AuthenticationPrincipal LoginMember me) {
        return ApiResponse.success(crawler.enqueue(me.getMemberId()));
    }
}
