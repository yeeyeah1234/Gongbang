package com.gongbang.workspace.whiteboard.dto;

import com.gongbang.workspace.whiteboard.repository.WhiteboardSummary;
import java.time.LocalDateTime;

/**
 * WB-01 목록 한 줄 (API 명세서: whiteboardId, title, creatorId, lastEditor?, updatedAt).
 *
 * <p>lastEditor: 동기화 서버가 본문을 저장한 적이 없으면 null. 탈퇴한 회원이면 nickname은 「탈퇴한 사용자」.
 */
public record WhiteboardListItemResponse(
        Long whiteboardId,
        String title,
        Long creatorId,
        LastEditor lastEditor,
        LocalDateTime updatedAt) {

    public record LastEditor(Long memberId, String nickname) {}

    public static WhiteboardListItemResponse of(WhiteboardSummary summary, LastEditor lastEditor) {
        return new WhiteboardListItemResponse(
                summary.id(),
                summary.title(),
                summary.creatorId(),
                lastEditor,
                summary.updatedAt());
    }
}
