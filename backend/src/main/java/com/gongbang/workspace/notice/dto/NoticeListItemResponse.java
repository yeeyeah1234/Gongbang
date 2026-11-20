package com.gongbang.workspace.notice.dto;

import com.gongbang.workspace.notice.domain.Notice;
import java.time.LocalDateTime;

/** NTC-01 목록 항목. preview는 내용의 첫 줄(최대 100자). 말줄임표는 화면이 붙인다. */
public record NoticeListItemResponse(
        Long noticeId,
        String title,
        String preview,
        NoticeAuthor author,
        LocalDateTime createdAt,
        boolean isPinned,
        boolean isEdited,
        boolean isUnread) {

    /** 미리보기 최대 글자 수 (문자 단위). */
    public static final int PREVIEW_MAX_LENGTH = 100;

    public static NoticeListItemResponse of(Notice notice, NoticeAuthor author, boolean unread) {
        return new NoticeListItemResponse(
                notice.getId(),
                notice.getTitle(),
                preview(notice.getContent()),
                author,
                notice.getCreatedAt(),
                notice.isPinned(),
                notice.isEdited(),
                unread);
    }

    /** 첫 줄만, 최대 100자. 이모지가 반으로 잘리지 않게 문자(code point) 단위로 자른다. */
    static String preview(String content) {
        if (content == null) {
            return "";
        }
        String firstLine = content.lines().findFirst().orElse("").strip();
        if (firstLine.codePointCount(0, firstLine.length()) <= PREVIEW_MAX_LENGTH) {
            return firstLine;
        }
        return firstLine.substring(0, firstLine.offsetByCodePoints(0, PREVIEW_MAX_LENGTH));
    }
}
