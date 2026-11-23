package com.gongbang.report.controller;

import com.gongbang.auth.domain.LoginMember;
import com.gongbang.global.common.ApiResponse;
import com.gongbang.report.dto.ReportCreateRequest;
import com.gongbang.report.dto.ReportCreateResponse;
import com.gongbang.report.service.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** NET-02 신고 API. */
@RestController
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /** NET-02 부적절한 회원·댓글·메시지 신고 접수 (`POST /api/reports`). */
    @PostMapping("/api/reports")
    public ApiResponse<ReportCreateResponse> createReport(
            @AuthenticationPrincipal LoginMember me,
            @Valid @RequestBody ReportCreateRequest request) {
        return ApiResponse.success(reportService.createReport(me.getMemberId(), request));
    }
}
