package com.gongbang.application.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gongbang.application.dto.ApplicationAcceptResponse;
import com.gongbang.application.dto.ApplicationCreateResponse;
import com.gongbang.application.service.ApplicantQueryService;
import com.gongbang.application.service.ApplicationCommandService;
import com.gongbang.auth.domain.LoginMember;
import com.gongbang.global.error.GlobalExceptionHandler;
import com.gongbang.member.domain.MemberRole;
import com.gongbang.post.domain.PostStatus;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 지원 API 경로·파라미터 연결과 에러 응답 (REC-001~005). */
@ExtendWith(MockitoExtension.class)
class ApplicationControllerTest {

    private static final Long MEMBER_ID = 7L;

    @Mock private ApplicationCommandService applicationCommandService;
    @Mock private ApplicantQueryService applicantQueryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(
                                new ApplicationController(
                                        applicationCommandService, applicantQueryService))
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
    @DisplayName("지원하기: 경로의 글 ID와 로그인 회원 ID를 넘기고 { applicationId }를 돌려준다")
    void 지원하기() throws Exception {
        login();
        given(applicationCommandService.apply(MEMBER_ID, 10L))
                .willReturn(new ApplicationCreateResponse(55L));

        mockMvc.perform(post("/api/posts/10/applications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.applicationId").value(55));
    }

    @Test
    @DisplayName("지원 취소: 성공하면 data null")
    void 지원_취소() throws Exception {
        login();

        mockMvc.perform(delete("/api/applications/55"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(applicationCommandService).cancel(MEMBER_ID, 55L);
    }

    @Test
    @DisplayName("지원자 목록: status 쿼리를 그대로 넘긴다")
    void 지원자_목록() throws Exception {
        login();

        mockMvc.perform(get("/api/posts/10/applications").param("status", "ACCEPTED"))
                .andExpect(status().isOk());

        verify(applicantQueryService).getApplicants(MEMBER_ID, 10L, "ACCEPTED");
    }

    @Test
    @DisplayName("지원자 목록은 GET /api/posts/**라 보안 설정상 공개지만, 비로그인이면 401 UNAUTHORIZED")
    void 지원자_목록_비로그인은_401() throws Exception {
        mockMvc.perform(get("/api/posts/10/applications"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        verifyNoInteractions(applicantQueryService);
    }

    @Test
    @DisplayName("수락: { teamId, currentCount, targetCount, postStatus }를 돌려준다")
    void 수락() throws Exception {
        login();
        given(applicationCommandService.accept(MEMBER_ID, 55L))
                .willReturn(new ApplicationAcceptResponse(300L, 4, 4, PostStatus.COMPLETED));

        mockMvc.perform(patch("/api/applications/55/accept"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.teamId").value(300))
                .andExpect(jsonPath("$.data.currentCount").value(4))
                .andExpect(jsonPath("$.data.targetCount").value(4))
                .andExpect(jsonPath("$.data.postStatus").value("COMPLETED"));
    }

    @Test
    @DisplayName("거절: 성공하면 data null")
    void 거절() throws Exception {
        login();

        mockMvc.perform(patch("/api/applications/55/reject")).andExpect(status().isOk());

        verify(applicationCommandService).reject(MEMBER_ID, 55L);
    }
}
