package com.gongbang.workspace.whiteboard.controller;

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

import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.GlobalExceptionHandler;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.workspace.whiteboard.service.WhiteboardSyncService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 동기화 서버용 보드 본문 API의 경로·바이트 본문·상태 코드 (WB-10). X-Sync-Key 확인은 SyncKeyFilterTest가(이미
 * /internal/whiteboards 포함), 규칙은 WhiteboardSyncServiceTest가 본다.
 */
@ExtendWith(MockitoExtension.class)
class InternalWhiteboardControllerTest {

    private static final byte[] BYTES = {0x00, 0x7F, (byte) 0x80, (byte) 0xFF};

    @Mock private WhiteboardSyncService whiteboardSyncService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(
                                new InternalWhiteboardController(whiteboardSyncService))
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
    }

    @Test
    @DisplayName("content GET: 바이트를 application/octet-stream으로 그대로 준다")
    void 불러오기_바이트() throws Exception {
        given(whiteboardSyncService.loadContent(600L)).willReturn(Optional.of(BYTES));

        mockMvc.perform(get("/internal/whiteboards/600/content"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(content().bytes(BYTES));
    }

    @Test
    @DisplayName("content GET: 비어 있으면 204")
    void 불러오기_비었음_204() throws Exception {
        given(whiteboardSyncService.loadContent(600L)).willReturn(Optional.empty());

        mockMvc.perform(get("/internal/whiteboards/600/content"))
                .andExpect(status().isNoContent())
                .andExpect(content().bytes(new byte[0]));
    }

    @Test
    @DisplayName("content GET: 삭제된 보드는 404 JSON 오류 (octet-stream을 원해도)")
    void 불러오기_삭제됨_404() throws Exception {
        given(whiteboardSyncService.loadContent(600L))
                .willThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        mockMvc.perform(
                        get("/internal/whiteboards/600/content")
                                .accept(
                                        MediaType.APPLICATION_OCTET_STREAM,
                                        MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("content PUT: 바이트와 lastEditorId를 그대로 넘긴다")
    void 저장_성공() throws Exception {
        mockMvc.perform(
                        put("/internal/whiteboards/600/content")
                                .param("lastEditorId", "7")
                                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                                .content(BYTES))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(whiteboardSyncService).saveContent(600L, 7L, BYTES);
    }

    @Test
    @DisplayName("content PUT: 삭제된 보드면 404")
    void 저장_삭제됨_404() throws Exception {
        willThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND))
                .given(whiteboardSyncService)
                .saveContent(eq(600L), eq(7L), any());

        mockMvc.perform(
                        put("/internal/whiteboards/600/content")
                                .param("lastEditorId", "7")
                                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                                .content(BYTES))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("content PUT: lastEditorId가 없으면 400, service를 부르지 않는다")
    void 저장_lastEditorId_없으면_400() throws Exception {
        mockMvc.perform(
                        put("/internal/whiteboards/600/content")
                                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                                .content(BYTES))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(whiteboardSyncService);
    }
}
