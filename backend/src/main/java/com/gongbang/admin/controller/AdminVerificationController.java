package com.gongbang.admin.controller;

import com.gongbang.auth.domain.LoginMember;
import com.gongbang.global.common.ApiResponse;
import com.gongbang.member.domain.VerifyStatus;
import com.gongbang.member.service.VerificationReviewService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/verifications")
@RequiredArgsConstructor
public class AdminVerificationController {
    private final VerificationReviewService service;

    @GetMapping
    public ApiResponse<VerificationReviewService.Result> list(
            @AuthenticationPrincipal LoginMember me,
            @RequestParam(defaultValue = "PENDING") VerifyStatus status,
            @RequestParam(defaultValue = "0") int page) {
        return ApiResponse.success(service.list(me.getMemberId(), status, page));
    }

    public record ReviewRequest(
            @NotNull VerifyStatus status, @Size(max = 200) String rejectReason) {}

    @GetMapping("/{requestId}")
    public org.springframework.http.ResponseEntity<ApiResponse<VerificationReviewService.Detail>>
            detail(@AuthenticationPrincipal LoginMember me, @PathVariable Long requestId) {
        return org.springframework.http.ResponseEntity.ok()
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .body(ApiResponse.success(service.detail(me.getMemberId(), requestId)));
    }

    @PatchMapping("/{requestId}")
    public ApiResponse<Void> review(
            @AuthenticationPrincipal LoginMember me,
            @PathVariable Long requestId,
            @Valid @RequestBody ReviewRequest body) {
        service.review(me.getMemberId(), requestId, body.status(), body.rejectReason());
        return ApiResponse.success();
    }
}
