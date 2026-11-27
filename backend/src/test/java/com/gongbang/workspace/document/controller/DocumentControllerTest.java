package com.gongbang.workspace.document.controller;

import static org.mockito.BDDMockito.given;
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
import com.gongbang.workspace.document.dto.DocumentListItemResponse;
import com.gongbang.workspace.document.dto.DocumentTitleResponse;
import com.gongbang.workspace.document.service.DocumentQueryService;
import com.gongbang.workspace.document.service.DocumentService;
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

/** 문서 API 경로·본문 연결과 상태 코드 (DOC-01~04). 권한·값 규칙은 service 테스트가 본다. */
@ExtendWith(MockitoExtension.class)
class DocumentControllerTest {

    private static final Long MEMBER_ID = 7L;

    @Mock private DocumentService documentService;
    @Mock private DocumentQueryService documentQueryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(
                                new DocumentController(documentService, documentQueryService))
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
    @DisplayName("목록: 로그인 회원과 팀 ID를 넘기고 문서 배열을 돌려준다")
    void 목록() throws Exception {
        given(documentQueryService.getDocuments(MEMBER_ID, 3L))
                .willReturn(
                        List.of(
                                new DocumentListItemResponse(
                                        100L,
                                        "회의록",
                                        "상현",
                                        LocalDateTime.of(2026, 11, 23, 10, 0),
                                        MEMBER_ID,
                                        true)));

        mockMvc.perform(get("/api/teams/3/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(100))
                .andExpect(jsonPath("$.data[0].title").value("회의록"))
                .andExpect(jsonPath("$.data[0].updatedByNickname").value("상현"))
                .andExpect(jsonPath("$.data[0].createdBy").value(7))
                .andExpect(jsonPath("$.data[0].canDelete").value(true));
    }

    @Test
    @DisplayName("생성: { documentId, title }을 돌려주고, 50개면 409 DOCUMENT_LIMIT")
    void 생성() throws Exception {
        given(documentService.create(MEMBER_ID, 3L))
                .willReturn(new DocumentTitleResponse(500L, "제목 없는 문서 (2)"))
                .willThrow(new BusinessException(ErrorCode.DOCUMENT_LIMIT));

        mockMvc.perform(post("/api/teams/3/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.documentId").value(500))
                .andExpect(jsonPath("$.data.title").value("제목 없는 문서 (2)"));
        mockMvc.perform(post("/api/teams/3/documents"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DOCUMENT_LIMIT"));
    }

    @Test
    @DisplayName("이름 변경: 본문의 title을 그대로 넘긴다")
    void 이름변경() throws Exception {
        given(documentService.rename(MEMBER_ID, 500L, "  회의록 ")).willReturn(500L);

        mockMvc.perform(
                        patch("/api/documents/500")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"title\": \"  회의록 \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.documentId").value(500));
    }

    @Test
    @DisplayName("삭제: 성공하면 200, 권한이 없으면 403 NOT_DOCUMENT_AUTHOR")
    void 삭제() throws Exception {
        mockMvc.perform(delete("/api/documents/500")).andExpect(status().isOk());
        verify(documentService).delete(MEMBER_ID, 500L);

        org.mockito.BDDMockito.willThrow(new BusinessException(ErrorCode.NOT_DOCUMENT_AUTHOR))
                .given(documentService)
                .delete(MEMBER_ID, 501L);
        mockMvc.perform(delete("/api/documents/501"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_DOCUMENT_AUTHOR"));
    }
}
