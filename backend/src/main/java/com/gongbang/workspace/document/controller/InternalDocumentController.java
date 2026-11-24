package com.gongbang.workspace.document.controller;

import com.gongbang.auth.domain.LoginMember;
import com.gongbang.global.common.ApiResponse;
import com.gongbang.workspace.document.dto.DocumentAccessResponse;
import com.gongbang.workspace.document.service.DocumentSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 동기화 서버 전용 문서 API (DOC-08·DOC-10). X-Sync-Key가 틀리면 {@code SyncKeyFilter}가 여기 오기 전에 401
 * INVALID_INTERNAL_KEY로 막는다 (AGENTS.md 9).
 *
 * <p>access: 동기화 서버가 브라우저에게 받은 access 토큰을 {@code Authorization: Bearer ...}로 그대로 넘긴다. 일반 API와 같은
 * {@code JwtAuthenticationFilter}가 읽어 {@link LoginMember}를 넣어 주고, 토큰이 없거나 틀리면 null이다. 키 없이 토큰만으로는
 * 열리지 않는다.
 *
 * <p>본문 GET·PUT은 ApiResponse로 감싸지 않은 바이트(application/octet-stream)다. 오류는 다른 API처럼 JSON이다. GET에
 * produces를 걸지 않은 것은, Accept가 octet-stream으로 고정되면 오류 JSON을 쓰지 못할 수 있어서다.
 */
@RestController
@RequiredArgsConstructor
public class InternalDocumentController {

    private final DocumentSyncService documentSyncService;

    /** DOC-08 문서 열기 권한 확인. */
    @GetMapping("/internal/documents/{documentId}/access")
    public ApiResponse<DocumentAccessResponse> checkAccess(
            @AuthenticationPrincipal LoginMember me, @PathVariable Long documentId) {
        Long memberId = me == null ? null : me.getMemberId();
        return ApiResponse.success(documentSyncService.checkAccess(memberId, documentId));
    }

    /** DOC-08 본문 불러오기: 바이트 그대로, 비어 있으면 204. */
    @GetMapping("/internal/documents/{documentId}/content")
    public ResponseEntity<byte[]> loadContent(@PathVariable Long documentId) {
        return documentSyncService
                .loadContent(documentId)
                .map(
                        content ->
                                ResponseEntity.ok()
                                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                                        .body(content))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** DOC-10 본문 저장. */
    @PutMapping(
            path = "/internal/documents/{documentId}/content",
            consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ApiResponse<Void> saveContent(
            @PathVariable Long documentId,
            @RequestParam Long editorId,
            @RequestBody(required = false) byte[] content) {
        documentSyncService.saveContent(documentId, editorId, content);
        return ApiResponse.success();
    }
}
