package com.gongbang.workspace.document.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gongbang.auth.domain.LoginMember;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.GlobalExceptionHandler;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.domain.MemberRole;
import com.gongbang.workspace.document.dto.DocumentAccessResponse;
import com.gongbang.workspace.document.service.DocumentSyncService;
import java.util.List;
import java.util.Optional;
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

/**
 * 동기화 서버용 문서 API의 경로·바이트 본문·상태 코드 (DOC-08·DOC-10). X-Sync-Key 확인은 SyncKeyFilterTest가, 권한 규칙은
 * DocumentSyncServiceTest가 본다.
 */
@ExtendWith(MockitoExtension.class)
class InternalDocumentControllerTest {

    private static final Long MEMBER_ID = 7L;
    private static final byte[] BYTES = {0x00, 0x7F, (byte) 0x80, (byte) 0xFF};

    @Mock private DocumentSyncService documentSyncService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(new InternalDocumentController(documentSyncService))
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
    @DisplayName("access: 토큰의 회원 ID를 넘기고 팀원 정보를 돌려준다")
    void access_성공() throws Exception {
        login();
        given(documentSyncService.checkAccess(MEMBER_ID, 500L))
                .willReturn(new DocumentAccessResponse(MEMBER_ID, "상현", 3L, false));

        mockMvc.perform(get("/internal/documents/500/access"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.memberId").value(7))
                .andExpect(jsonPath("$.data.nickname").value("상현"))
                .andExpect(jsonPath("$.data.teamId").value(3))
                .andExpect(jsonPath("$.data.leader").value(false));
    }

    @Test
    @DisplayName("access: 토큰이 없으면 null을 넘기고 service의 401을 그대로 낸다")
    void access_토큰없음_401() throws Exception {
        given(documentSyncService.checkAccess(null, 500L))
                .willThrow(new BusinessException(ErrorCode.UNAUTHORIZED));

        mockMvc.perform(get("/internal/documents/500/access"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("content GET: 바이트를 application/octet-stream으로 그대로 준다")
    void 불러오기_바이트() throws Exception {
        given(documentSyncService.loadContent(500L)).willReturn(Optional.of(BYTES));

        mockMvc.perform(get("/internal/documents/500/content"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(content().bytes(BYTES));
    }

    @Test
    @DisplayName("content GET: 비어 있으면 204")
    void 불러오기_비었음_204() throws Exception {
        given(documentSyncService.loadContent(500L)).willReturn(Optional.empty());

        mockMvc.perform(get("/internal/documents/500/content"))
                .andExpect(status().isNoContent())
                .andExpect(content().bytes(new byte[0]));
    }

    @Test
    @DisplayName("content GET: 삭제된 문서는 404 JSON 오류 (octet-stream을 원해도)")
    void 불러오기_삭제됨_404() throws Exception {
        given(documentSyncService.loadContent(500L))
                .willThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        mockMvc.perform(
                        get("/internal/documents/500/content")
                                .accept(
                                        MediaType.APPLICATION_OCTET_STREAM,
                                        MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("content PUT: 바이트와 editorId를 그대로 넘긴다")
    void 저장_성공() throws Exception {
        mockMvc.perform(
                        put("/internal/documents/500/content")
                                .param("editorId", "7")
                                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                                .content(BYTES))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(documentSyncService).saveContent(500L, 7L, BYTES);
    }

    @Test
    @DisplayName("content PUT: 삭제된 문서면 404")
    void 저장_삭제됨_404() throws Exception {
        willThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND))
                .given(documentSyncService)
                .saveContent(eq(500L), eq(7L), any());

        mockMvc.perform(
                        put("/internal/documents/500/content")
                                .param("editorId", "7")
                                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                                .content(BYTES))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("content PUT: editorId가 없으면 400, service를 부르지 않는다")
    void 저장_editorId_없으면_400() throws Exception {
        mockMvc.perform(
                        put("/internal/documents/500/content")
                                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                                .content(BYTES))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(documentSyncService);
    }
}
