package com.gongbang.workspace.whiteboard.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gongbang.auth.domain.LoginMember;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.GlobalExceptionHandler;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.domain.MemberRole;
import com.gongbang.workspace.whiteboard.dto.WhiteboardAccessResponse;
import com.gongbang.workspace.whiteboard.dto.WhiteboardListItemResponse;
import com.gongbang.workspace.whiteboard.dto.WhiteboardListItemResponse.LastEditor;
import com.gongbang.workspace.whiteboard.dto.WhiteboardTitleResponse;
import com.gongbang.workspace.whiteboard.service.WhiteboardQueryService;
import com.gongbang.workspace.whiteboard.service.WhiteboardService;
import com.gongbang.workspace.whiteboard.service.WhiteboardSyncService;
import java.time.LocalDateTime;
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

/** 화이트보드 API 경로·필드 이름(API 명세서 WB-01·WB-10)과 상태 코드. 권한·값 규칙은 service 테스트가 본다. */
@ExtendWith(MockitoExtension.class)
class WhiteboardControllerTest {

    private static final Long MEMBER_ID = 7L;

    @Mock private WhiteboardService whiteboardService;
    @Mock private WhiteboardQueryService whiteboardQueryService;
    @Mock private WhiteboardSyncService whiteboardSyncService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(
                                new WhiteboardController(
                                        whiteboardService,
                                        whiteboardQueryService,
                                        whiteboardSyncService))
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                        .build();
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                new LoginMember(MEMBER_ID, MemberRole.USER), null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName(
            "목록: whiteboardId·title·creatorId·lastEditor·updatedAt을 돌려준다 (lastEditor는 없으면 null)")
    void 목록() throws Exception {
        LocalDateTime at = LocalDateTime.of(2026, 11, 24, 10, 0);
        given(whiteboardQueryService.getWhiteboards(MEMBER_ID, 3L))
                .willReturn(
                        List.of(
                                new WhiteboardListItemResponse(
                                        100L, "아이디어", MEMBER_ID, new LastEditor(8L, "상현"), at),
                                new WhiteboardListItemResponse(99L, "제목 없는 보드", 8L, null, at)));

        mockMvc.perform(get("/api/teams/3/whiteboards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].whiteboardId").value(100))
                .andExpect(jsonPath("$.data[0].title").value("아이디어"))
                .andExpect(jsonPath("$.data[0].creatorId").value(7))
                .andExpect(jsonPath("$.data[0].lastEditor.memberId").value(8))
                .andExpect(jsonPath("$.data[0].lastEditor.nickname").value("상현"))
                .andExpect(jsonPath("$.data[0].updatedAt").exists())
                .andExpect(jsonPath("$.data[1].lastEditor").isEmpty());
    }

    @Test
    @DisplayName("만들기: { whiteboardId, title }을 돌려주고, 30개면 409 WHITEBOARD_LIMIT")
    void 만들기() throws Exception {
        given(whiteboardService.create(MEMBER_ID, 3L))
                .willReturn(new WhiteboardTitleResponse(600L, "제목 없는 보드"))
                .willThrow(new BusinessException(ErrorCode.WHITEBOARD_LIMIT));

        mockMvc.perform(post("/api/teams/3/whiteboards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.whiteboardId").value(600))
                .andExpect(jsonPath("$.data.title").value("제목 없는 보드"));
        mockMvc.perform(post("/api/teams/3/whiteboards"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("WHITEBOARD_LIMIT"));
    }

    @Test
    @DisplayName("제목 변경: 본문의 title을 그대로 넘기고 { whiteboardId, title }을 돌려준다")
    void 제목변경() throws Exception {
        given(whiteboardService.rename(MEMBER_ID, 600L, "  아이디어 "))
                .willReturn(new WhiteboardTitleResponse(600L, "아이디어"));

        mockMvc.perform(
                        patch("/api/whiteboards/600")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"title\": \"  아이디어 \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.whiteboardId").value(600))
                .andExpect(jsonPath("$.data.title").value("아이디어"));
    }

    @Test
    @DisplayName("삭제: 성공하면 200, 권한이 없으면 403 NOT_WHITEBOARD_AUTHOR")
    void 삭제() throws Exception {
        mockMvc.perform(delete("/api/whiteboards/600")).andExpect(status().isOk());
        verify(whiteboardService).delete(MEMBER_ID, 600L);

        willThrow(new BusinessException(ErrorCode.NOT_WHITEBOARD_AUTHOR))
                .given(whiteboardService)
                .delete(MEMBER_ID, 601L);
        mockMvc.perform(delete("/api/whiteboards/601"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_WHITEBOARD_AUTHOR"));
    }

    @Test
    @DisplayName("입장 확인: GET /api/whiteboards/{id}/access가 명세서 필드를 돌려준다")
    void 입장확인() throws Exception {
        given(whiteboardSyncService.checkAccess(MEMBER_ID, 600L))
                .willReturn(new WhiteboardAccessResponse(600L, 3L, MEMBER_ID, "규선", 2));

        mockMvc.perform(get("/api/whiteboards/600/access"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.whiteboardId").value(600))
                .andExpect(jsonPath("$.data.teamId").value(3))
                .andExpect(jsonPath("$.data.memberId").value(7))
                .andExpect(jsonPath("$.data.nickname").value("규선"))
                .andExpect(jsonPath("$.data.colorIndex").value(2));
    }
}
