package com.gongbang.application.controller;

import com.gongbang.application.dto.ApplicantListResponse;
import com.gongbang.application.dto.ApplicationAcceptResponse;
import com.gongbang.application.dto.ApplicationCreateResponse;
import com.gongbang.application.service.ApplicantQueryService;
import com.gongbang.application.service.ApplicationCommandService;
import com.gongbang.auth.domain.LoginMember;
import com.gongbang.global.common.ApiResponse;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 지원 API (API 명세서 REC-001~005). 값 검증과 권한 확인은 service가 한다. */
@RestController
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationCommandService applicationCommandService;
    private final ApplicantQueryService applicantQueryService;

    /** REC-001: 로그인. */
    @PostMapping("/api/posts/{postId}/applications")
    public ApiResponse<ApplicationCreateResponse> apply(
            @AuthenticationPrincipal LoginMember me, @PathVariable Long postId) {
        return ApiResponse.success(applicationCommandService.apply(me.getMemberId(), postId));
    }

    /** REC-002: 지원자 본인. */
    @DeleteMapping("/api/applications/{applicationId}")
    public ApiResponse<Void> cancel(
            @AuthenticationPrincipal LoginMember me, @PathVariable Long applicationId) {
        applicationCommandService.cancel(me.getMemberId(), applicationId);
        return ApiResponse.success();
    }

    /**
     * REC-003: 작성자. status를 안 보내면 대기 탭.
     *
     * <p>보안 설정이 GET /api/posts/**를 공개로 열어 두어 비로그인 요청도 여기까지 오므로, 로그인 확인을 여기서 한다 (401).
     */
    @GetMapping("/api/posts/{postId}/applications")
    public ApiResponse<ApplicantListResponse> getApplicants(
            @AuthenticationPrincipal LoginMember me,
            @PathVariable Long postId,
            @RequestParam(required = false) String status) {
        if (me == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return ApiResponse.success(
                applicantQueryService.getApplicants(me.getMemberId(), postId, status));
    }

    /** REC-004: 작성자. */
    @PatchMapping("/api/applications/{applicationId}/accept")
    public ApiResponse<ApplicationAcceptResponse> accept(
            @AuthenticationPrincipal LoginMember me, @PathVariable Long applicationId) {
        return ApiResponse.success(
                applicationCommandService.accept(me.getMemberId(), applicationId));
    }

    /** REC-005: 작성자. */
    @PatchMapping("/api/applications/{applicationId}/reject")
    public ApiResponse<Void> reject(
            @AuthenticationPrincipal LoginMember me, @PathVariable Long applicationId) {
        applicationCommandService.reject(me.getMemberId(), applicationId);
        return ApiResponse.success();
    }
}
