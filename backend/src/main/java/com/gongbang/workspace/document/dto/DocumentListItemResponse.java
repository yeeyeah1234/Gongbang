package com.gongbang.workspace.document.dto;

import com.gongbang.workspace.document.repository.DocumentSummary;
import java.time.LocalDateTime;

/**
 * DOC-01 목록 한 줄.
 *
 * <ul>
 *   <li>updatedByNickname: 마지막으로 본문을 저장했거나 이름을 바꾼 회원. 탈퇴했으면 「탈퇴한 사용자」.
 *   <li>canDelete: 지금 요청한 사람이 지울 수 있는지 (작성자 또는 팀장, DOC-04).
 * </ul>
 */
public record DocumentListItemResponse(
        Long id,
        String title,
        String updatedByNickname,
        LocalDateTime updatedAt,
        Long createdBy,
        boolean canDelete) {

    public static DocumentListItemResponse of(
            DocumentSummary summary, String updatedByNickname, boolean canDelete) {
        return new DocumentListItemResponse(
                summary.id(),
                summary.title(),
                updatedByNickname,
                summary.updatedAt(),
                summary.createdBy(),
                canDelete);
    }
}
