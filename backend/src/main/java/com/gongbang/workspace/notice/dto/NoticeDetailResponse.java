package com.gongbang.workspace.notice.dto;

import com.gongbang.workspace.notice.domain.Notice;
import java.time.LocalDateTime;

/** NTC-02 상세, NTC-05 고정 배너. editedAt이 있으면 「(수정됨)」. */
public record NoticeDetailResponse(
        Long noticeId,
        Long teamId,
        String title,
        String content,
        NoticeAuthor author,
        LocalDateTime createdAt,
        LocalDateTime editedAt,
        boolean isPinned,
        boolean isEdited) {

    public static NoticeDetailResponse of(Notice notice, NoticeAuthor author) {
        return new NoticeDetailResponse(
                notice.getId(),
                notice.getTeamId(),
                notice.getTitle(),
                notice.getContent(),
                author,
                notice.getCreatedAt(),
                notice.getEditedAt(),
                notice.isPinned(),
                notice.isEdited());
    }
}
