package com.gongbang.report.dto;

import com.gongbang.report.domain.ReportReasonCode;
import com.gongbang.report.domain.ReportTargetType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** NET-02 신고 접수 요청 (`POST /api/reports`). */
public record ReportCreateRequest(
        @NotNull(message = "신고 대상 회원을 선택해 주세요.") Long reportedMemberId,
        @NotNull(message = "신고 대상을 지정해 주세요.") ReportTargetType targetType,
        Long targetId,
        @NotNull(message = "신고 사유를 선택해 주세요.") ReportReasonCode reasonCode,
        @Size(max = 500, message = "신고 상세 내용은 500자까지 입력할 수 있어요.") String detail) {}
