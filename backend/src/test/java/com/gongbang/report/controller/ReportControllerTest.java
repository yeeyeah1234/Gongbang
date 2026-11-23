package com.gongbang.report.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gongbang.auth.domain.LoginMember;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.GlobalExceptionHandler;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.domain.MemberRole;
import com.gongbang.report.dto.ReportCreateResponse;
import com.gongbang.report.service.ReportService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ReportControllerTest {

    private static final Long MEMBER_ID = 7L;

    @Mock private ReportService reportService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(new ReportController(reportService))
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                        .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static void login() {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                new LoginMember(MEMBER_ID, MemberRole.USER), null, List.of()));
    }

    @Test
    @DisplayName("신고 접수 성공 시 reportId를 반환한다")
    void 신고_성공() throws Exception {
        login();
        given(reportService.createReport(eq(MEMBER_ID), any()))
                .willReturn(new ReportCreateResponse(33L));

        mockMvc.perform(
                        post("/api/reports")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "reportedMemberId": 12,
                                          "targetType": "MEMBER",
                                          "reasonCode": "ABUSE",
                                          "detail": "채팅에서 비속어 사용"
                                        }
                                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.reportId").value(33));
    }

    @Test
    @DisplayName("자기 자신 신고 예외 시 400 CANNOT_REPORT_SELF를 반환한다")
    void 본인신고_400() throws Exception {
        login();
        given(reportService.createReport(eq(MEMBER_ID), any()))
                .willThrow(new BusinessException(ErrorCode.CANNOT_REPORT_SELF));

        mockMvc.perform(
                        post("/api/reports")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "reportedMemberId": 7,
                                          "targetType": "MEMBER",
                                          "reasonCode": "BAD_NICKNAME"
                                        }
                                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("CANNOT_REPORT_SELF"));
    }

    @Test
    @DisplayName("필수 필드 누락 시 400 INVALID_INPUT을 반환한다")
    void 유효성실패_400() throws Exception {
        login();

        mockMvc.perform(
                        post("/api/reports")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "reportedMemberId": 12
                                        }
                                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }
}
