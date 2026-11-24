package com.gongbang.workspace.whiteboard.controller;

import com.gongbang.global.common.ApiResponse;
import com.gongbang.workspace.whiteboard.service.WhiteboardSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 동기화 서버 전용 화이트보드 본문 API (WB-10). X-Sync-Key가 틀리면 {@code SyncKeyFilter}가 여기 오기 전에 401
 * INVALID_INTERNAL_KEY로 막는다 (AGENTS.md 9).
 *
 * <p>본문 GET·PUT은 ApiResponse로 감싸지 않은 바이트(application/octet-stream)다. 오류는 다른 API처럼 JSON이다 ({@code
 * InternalDocumentController}와 같음).
 */
@RestController
@RequiredArgsConstructor
public class InternalWhiteboardController {

    private final WhiteboardSyncService whiteboardSyncService;

    /** WB-10 본문 불러오기: 바이트 그대로, 비어 있으면 204. */
    @GetMapping("/internal/whiteboards/{whiteboardId}/content")
    public ResponseEntity<byte[]> loadContent(@PathVariable Long whiteboardId) {
        return whiteboardSyncService
                .loadContent(whiteboardId)
                .map(
                        content ->
                                ResponseEntity.ok()
                                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                                        .body(content))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** WB-10 본문 저장 (query: lastEditorId, API 명세서). */
    @PutMapping(
            path = "/internal/whiteboards/{whiteboardId}/content",
            consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ApiResponse<Void> saveContent(
            @PathVariable Long whiteboardId,
            @RequestParam Long lastEditorId,
            @RequestBody(required = false) byte[] content) {
        whiteboardSyncService.saveContent(whiteboardId, lastEditorId, content);
        return ApiResponse.success();
    }
}
