package com.gongbang.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.domain.Member;
import com.gongbang.member.service.MemberService;
import com.gongbang.report.domain.Report;
import com.gongbang.report.domain.ReportReasonCode;
import com.gongbang.report.domain.ReportStatus;
import com.gongbang.report.domain.ReportTargetType;
import com.gongbang.report.dto.ReportCreateRequest;
import com.gongbang.report.dto.ReportCreateResponse;
import com.gongbang.report.repository.ReportRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock private ReportRepository reportRepository;
    @Mock private MemberService memberService;
    @Mock private Member activeTarget;

    private ReportService reportService;

    @BeforeEach
    void setUp() {
        reportService = new ReportService(reportRepository, memberService);
    }

    @Test
    @DisplayName("회원 신고 시 초기 상태 RECEIVED로 저장되고 reportId를 반환한다")
    void 회원_신고_정상() {
        given(memberService.findActiveMember(2L)).willReturn(Optional.of(activeTarget));
        given(reportRepository.save(any(Report.class)))
                .willAnswer(
                        inv -> {
                            Report report = inv.getArgument(0);
                            ReflectionTestUtils.setField(report, "id", 101L);
                            return report;
                        });

        ReportCreateResponse response =
                reportService.createReport(
                        1L,
                        new ReportCreateRequest(
                                2L,
                                ReportTargetType.MEMBER,
                                null,
                                ReportReasonCode.ABUSE,
                                "   욕설 반복   "));

        assertThat(response.reportId()).isEqualTo(101L);

        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).save(captor.capture());
        Report saved = captor.getValue();
        assertThat(saved.getReporterId()).isEqualTo(1L);
        assertThat(saved.getReportedMemberId()).isEqualTo(2L);
        assertThat(saved.getTargetType()).isEqualTo(ReportTargetType.MEMBER);
        assertThat(saved.getReasonCode()).isEqualTo(ReportReasonCode.ABUSE);
        assertThat(saved.getDetail()).isEqualTo("욕설 반복");
        assertThat(saved.getStatus()).isEqualTo(ReportStatus.RECEIVED);
    }

    @Test
    @DisplayName("공백만 있는 detail은 null로 정규화된다")
    void 공백_detail_null처리() {
        given(memberService.findActiveMember(2L)).willReturn(Optional.of(activeTarget));
        given(reportRepository.save(any(Report.class)))
                .willAnswer(
                        inv -> {
                            Report report = inv.getArgument(0);
                            ReflectionTestUtils.setField(report, "id", 102L);
                            return report;
                        });

        reportService.createReport(
                1L,
                new ReportCreateRequest(
                        2L, ReportTargetType.COMMENT, 55L, ReportReasonCode.HATE, "   "));

        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).save(captor.capture());
        assertThat(captor.getValue().getTargetId()).isEqualTo(55L);
        assertThat(captor.getValue().getDetail()).isNull();
    }

    @Test
    @DisplayName("자기 자신을 신고하면 400 CANNOT_REPORT_SELF가 발생한다")
    void 자기자신_신고_차단() {
        BusinessException ex =
                assertThrows(
                        BusinessException.class,
                        () ->
                                reportService.createReport(
                                        1L,
                                        new ReportCreateRequest(
                                                1L,
                                                ReportTargetType.MEMBER,
                                                null,
                                                ReportReasonCode.BAD_NICKNAME,
                                                null)));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CANNOT_REPORT_SELF);
        verifyNoInteractions(reportRepository);
    }

    @Test
    @DisplayName("댓글이나 메시지 신고에 targetId가 없으면 400 INVALID_INPUT이 발생한다")
    void 대상ID_누락_차단() {
        BusinessException ex =
                assertThrows(
                        BusinessException.class,
                        () ->
                                reportService.createReport(
                                        1L,
                                        new ReportCreateRequest(
                                                2L,
                                                ReportTargetType.MESSAGE,
                                                null,
                                                ReportReasonCode.ABUSE,
                                                null)));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
        verifyNoInteractions(reportRepository);
    }

    @Test
    @DisplayName("신고 대상 회원이 없거나 탈퇴했으면 404 MEMBER_NOT_FOUND가 발생한다")
    void 없는회원_신고_404() {
        given(memberService.findActiveMember(999L)).willReturn(Optional.empty());

        BusinessException ex =
                assertThrows(
                        BusinessException.class,
                        () ->
                                reportService.createReport(
                                        1L,
                                        new ReportCreateRequest(
                                                999L,
                                                ReportTargetType.MEMBER,
                                                null,
                                                ReportReasonCode.HATE,
                                                null)));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
        verifyNoInteractions(reportRepository);
    }
}
