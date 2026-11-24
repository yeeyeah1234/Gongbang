package com.gongbang.report.service;

import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.service.MemberService;
import com.gongbang.report.domain.Report;
import com.gongbang.report.domain.ReportTargetType;
import com.gongbang.report.dto.ReportCreateRequest;
import com.gongbang.report.dto.ReportCreateResponse;
import com.gongbang.report.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** NET-02 신고 접수 서비스. */
@Service
@RequiredArgsConstructor
public class ReportService {

    private static final int MAX_DETAIL_LENGTH = 500;

    private final ReportRepository reportRepository;
    private final MemberService memberService;
    private final ReportTargetValidationService targets;

    /** 신고를 검증하고 저장한다. 본인 신고는 400 `CANNOT_REPORT_SELF`, 없는/탈퇴 회원은 404 `MEMBER_NOT_FOUND`. */
    @Transactional
    public ReportCreateResponse createReport(Long reporterId, ReportCreateRequest request) {
        if (reporterId.equals(request.reportedMemberId())) {
            throw new BusinessException(ErrorCode.CANNOT_REPORT_SELF);
        }
        if (request.targetType() != ReportTargetType.MEMBER && request.targetId() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "신고 대상 ID가 필요합니다.");
        }
        if (memberService.findActiveMember(request.reportedMemberId()).isEmpty()) {
            throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND, "신고 대상 회원을 찾을 수 없어요");
        }

        targets.validate(reporterId, request);
        String normalizedDetail = normalizeDetail(request.detail());
        Report saved =
                reportRepository.save(
                        Report.create(
                                reporterId,
                                request.reportedMemberId(),
                                request.targetType(),
                                request.targetId(),
                                request.reasonCode(),
                                normalizedDetail));
        return new ReportCreateResponse(saved.getId());
    }

    private static String normalizeDetail(String detail) {
        if (detail == null) {
            return null;
        }
        String trimmed = detail.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > MAX_DETAIL_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "신고 상세 내용은 500자까지 입력할 수 있어요.");
        }
        return trimmed;
    }
}
