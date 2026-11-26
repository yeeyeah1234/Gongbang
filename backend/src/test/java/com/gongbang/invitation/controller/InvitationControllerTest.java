package com.gongbang.invitation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gongbang.auth.domain.LoginMember;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.GlobalExceptionHandler;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.invitation.dto.InvitationAcceptResponse;
import com.gongbang.invitation.dto.InvitationCreateResponse;
import com.gongbang.invitation.dto.RecruitingPostResponse;
import com.gongbang.invitation.service.InvitationCommandService;
import com.gongbang.invitation.service.InvitationQueryService;
import com.gongbang.member.domain.MemberRole;
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
class InvitationControllerTest {

    private static final Long MEMBER_ID = 7L;

    @Mock private InvitationQueryService invitationQueryService;
    @Mock private InvitationCommandService invitationCommandService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(
                                new InvitationController(
                                        invitationQueryService, invitationCommandService))
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
    @DisplayName("GET /api/members/me/recruiting-posts는 내 모집중 글 목록을 반환한다")
    void 내모집글_조회() throws Exception {
        login();
        given(invitationQueryService.getMyRecruitingPosts(MEMBER_ID))
                .willReturn(List.of(new RecruitingPostResponse(10L, "팀원 구함", 1, 4)));

        mockMvc.perform(get("/api/members/me/recruiting-posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].postId").value(10))
                .andExpect(jsonPath("$.data[0].title").value("팀원 구함"))
                .andExpect(jsonPath("$.data[0].currentCount").value(1))
                .andExpect(jsonPath("$.data[0].targetCount").value(4));
    }

    @Test
    @DisplayName("POST /api/invitations는 초대 생성 후 invitationId를 반환한다")
    void 초대_생성() throws Exception {
        login();
        given(invitationCommandService.invite(eq(MEMBER_ID), any()))
                .willReturn(new InvitationCreateResponse(51L));

        mockMvc.perform(
                        post("/api/invitations")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"postId\": 10, \"inviteeId\": 22}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.invitationId").value(51));
    }

    @Test
    @DisplayName(
            "POST /api/invitations/{id}/accept·decline을 정상 처리하고 마감 초대는 409 INVITATION_CLOSED를 반환한다")
    void 초대_수락_거절() throws Exception {
        login();
        given(invitationCommandService.accept(51L, MEMBER_ID))
                .willReturn(new InvitationAcceptResponse(900L));

        mockMvc.perform(post("/api/invitations/51/accept"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.teamId").value(900));

        mockMvc.perform(post("/api/invitations/51/decline"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        verify(invitationCommandService).decline(51L, MEMBER_ID);

        given(invitationCommandService.accept(99L, MEMBER_ID))
                .willThrow(new BusinessException(ErrorCode.INVITATION_CLOSED));
        mockMvc.perform(post("/api/invitations/99/accept"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVITATION_CLOSED"));
    }
}
