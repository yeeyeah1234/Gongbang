package com.gongbang.admin.controller;

import com.gongbang.auth.domain.LoginMember;
import com.gongbang.comment.service.CommentModerationService;
import com.gongbang.global.common.ApiResponse;
import com.gongbang.member.service.AdminMemberService;
import com.gongbang.post.service.AdminPostService;
import com.gongbang.report.service.AdminReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class AdminModerationController {
    private final AdminPostService posts;
    private final AdminReportService reports;
    private final CommentModerationService comments;
    private final AdminMemberService access;

    @GetMapping("/posts")
    public ApiResponse<?> posts(
            @AuthenticationPrincipal LoginMember me,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "true") boolean reportedFirst,
            @RequestParam(defaultValue = "0") int page) {
        return ApiResponse.success(posts.list(me.getMemberId(), keyword, reportedFirst, page));
    }

    @PostMapping("/posts/{id}/hide")
    public ApiResponse<?> hide(@AuthenticationPrincipal LoginMember me, @PathVariable Long id) {
        posts.hide(me.getMemberId(), id);
        return ApiResponse.success();
    }

    @GetMapping("/posts/{id}/comments")
    public ApiResponse<?> comments(@AuthenticationPrincipal LoginMember me, @PathVariable Long id) {
        access.requireAdmin(me.getMemberId());
        return ApiResponse.success(comments.list(id));
    }

    @PostMapping("/comments/{id}/hide")
    public ApiResponse<?> hideComment(
            @AuthenticationPrincipal LoginMember me, @PathVariable Long id) {
        comments.hide(me.getMemberId(), id);
        return ApiResponse.success();
    }

    @GetMapping("/reports")
    public ApiResponse<?> reports(
            @AuthenticationPrincipal LoginMember me,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "") String type,
            @RequestParam(defaultValue = "0") int page) {
        return ApiResponse.success(reports.list(me.getMemberId(), status, type, page));
    }

    @GetMapping("/reports/{id}")
    public ApiResponse<?> detail(@AuthenticationPrincipal LoginMember me, @PathVariable Long id) {
        return ApiResponse.success(reports.detail(me.getMemberId(), id));
    }

    @PostMapping("/reports/{id}/review")
    public ApiResponse<?> review(
            @AuthenticationPrincipal LoginMember me,
            @PathVariable Long id,
            @RequestBody AdminReportService.Review body) {
        return ApiResponse.success(reports.review(me.getMemberId(), id, body));
    }

    @PostMapping("/reports/{id}/cancel-review")
    public ApiResponse<?> cancelReview(
            @AuthenticationPrincipal LoginMember me,
            @PathVariable Long id,
            @RequestBody AdminReportService.Review body) {
        return ApiResponse.success(reports.cancelReview(me.getMemberId(), id, body));
    }
}
