package com.gongbang.workspace.whiteboard.controller;

import com.gongbang.auth.domain.LoginMember;
import com.gongbang.global.common.ApiResponse;
import com.gongbang.workspace.whiteboard.dto.WhiteboardAccessResponse;
import com.gongbang.workspace.whiteboard.dto.WhiteboardListItemResponse;
import com.gongbang.workspace.whiteboard.dto.WhiteboardRenameRequest;
import com.gongbang.workspace.whiteboard.dto.WhiteboardTitleResponse;
import com.gongbang.workspace.whiteboard.service.WhiteboardQueryService;
import com.gongbang.workspace.whiteboard.service.WhiteboardService;
import com.gongbang.workspace.whiteboard.service.WhiteboardSyncService;
import java.util.List;
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
 * 팀 화이트보드 API (WB-01, WB-10 입장 확인). 경로는 API 명세서 그대로: 목록·만들기는 /teams/{teamId}/whiteboards, 보드 하나는
 * /whiteboards/{whiteboardId}. 값 검증과 권한 확인은 service가 한다 (목록·만들기·제목 변경·입장 = 팀원, 삭제 = 만든 사람 또는 팀장).
 * 본문 불러오기·저장은 {@link InternalWhiteboardController}.
 */
@RestController
@RequiredArgsConstructor
public class WhiteboardController {

    private final WhiteboardService whiteboardService;
    private final WhiteboardQueryService whiteboardQueryService;
    private final WhiteboardSyncService whiteboardSyncService;

    /** WB-01 목록. */
    @GetMapping("/api/teams/{teamId}/whiteboards")
    public ApiResponse<List<WhiteboardListItemResponse>> getWhiteboards(
            @AuthenticationPrincipal LoginMember me, @PathVariable Long teamId) {
        return ApiResponse.success(whiteboardQueryService.getWhiteboards(me.getMemberId(), teamId));
    }

    /** WB-01 ② 만들기 (제목 「제목 없는 보드」, 빈 캔버스). */
    @PostMapping("/api/teams/{teamId}/whiteboards")
    public ApiResponse<WhiteboardTitleResponse> create(
            @AuthenticationPrincipal LoginMember me, @PathVariable Long teamId) {
        return ApiResponse.success(whiteboardService.create(me.getMemberId(), teamId));
    }

    /** WB-01 ① 제목 변경. */
    @PatchMapping("/api/whiteboards/{whiteboardId}")
    public ApiResponse<WhiteboardTitleResponse> rename(
            @AuthenticationPrincipal LoginMember me,
            @PathVariable Long whiteboardId,
            @RequestBody WhiteboardRenameRequest request) {
        return ApiResponse.success(
                whiteboardService.rename(me.getMemberId(), whiteboardId, request.title()));
    }

    /** WB-01 ① 삭제 (소프트 삭제). */
    @DeleteMapping("/api/whiteboards/{whiteboardId}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal LoginMember me, @PathVariable Long whiteboardId) {
        whiteboardService.delete(me.getMemberId(), whiteboardId);
        return ApiResponse.success();
    }

    /** WB-10 동기화 서버 입장 확인. 동기화 서버가 사용자 access 토큰을 Authorization 헤더로 그대로 넘긴다. */
    @GetMapping("/api/whiteboards/{whiteboardId}/access")
    public ApiResponse<WhiteboardAccessResponse> checkAccess(
            @AuthenticationPrincipal LoginMember me, @PathVariable Long whiteboardId) {
        Long memberId = me == null ? null : me.getMemberId();
        return ApiResponse.success(whiteboardSyncService.checkAccess(memberId, whiteboardId));
    }
}
