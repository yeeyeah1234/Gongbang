package com.gongbang.workspace.document.controller;

import com.gongbang.auth.domain.LoginMember;
import com.gongbang.global.common.ApiResponse;
import com.gongbang.workspace.document.dto.DocumentListItemResponse;
import com.gongbang.workspace.document.dto.DocumentRenameRequest;
import com.gongbang.workspace.document.dto.DocumentTitleResponse;
import com.gongbang.workspace.document.service.DocumentQueryService;
import com.gongbang.workspace.document.service.DocumentService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 팀 문서 API (DOC-01~04). 캘린더처럼 목록·생성은 팀 기준(/teams/{teamId}), 문서 하나는 /documents/{documentId}로 다룬다. 값
 * 검증과 권한 확인은 service가 한다 (목록·생성·이름 변경 = 팀원, 삭제 = 작성자 또는 팀장). 본문은 동기화 서버가 /internal API로 다룬다.
 */
@RestController
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentQueryService documentQueryService;

    /** DOC-01 목록. */
    @GetMapping("/api/teams/{teamId}/documents")
    public ApiResponse<List<DocumentListItemResponse>> getDocuments(
            @AuthenticationPrincipal LoginMember me, @PathVariable Long teamId) {
        return ApiResponse.success(documentQueryService.getDocuments(me.getMemberId(), teamId));
    }

    /** DOC-02 생성 (기본 제목 번호 포함, 빈 본문). */
    @PostMapping("/api/teams/{teamId}/documents")
    public ApiResponse<DocumentTitleResponse> create(
            @AuthenticationPrincipal LoginMember me, @PathVariable Long teamId) {
        return ApiResponse.success(documentService.create(me.getMemberId(), teamId));
    }

    /** DOC-03 이름 변경. */
    @PatchMapping("/api/documents/{documentId}")
    public ApiResponse<Map<String, Long>> rename(
            @AuthenticationPrincipal LoginMember me,
            @PathVariable Long documentId,
            @RequestBody DocumentRenameRequest request) {
        return ApiResponse.success(
                Map.of(
                        "documentId",
                        documentService.rename(me.getMemberId(), documentId, request.title())));
    }

    /** DOC-04 삭제 (소프트 삭제). */
    @DeleteMapping("/api/documents/{documentId}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal LoginMember me, @PathVariable Long documentId) {
        documentService.delete(me.getMemberId(), documentId);
        return ApiResponse.success();
    }
}
