package com.gongbang.workspace.calendar.dto;

import com.gongbang.workspace.calendar.domain.CalendarEvent;
import com.gongbang.workspace.calendar.domain.CalendarEventAutoType;
import java.time.LocalDateTime;

/**
 * CAL-03 목록·상세 공통. 종일 일정은 「종료일 포함」 방식(endAt = 마지막 날 00:00)이다.
 *
 * <ul>
 *   <li>자동 일정(autoType 있음): createdBy·authorNickname null, canEdit 항상 false.
 *   <li>작성자 회원 행이 지워진 일반 일정: createdBy null, authorNickname 「탈퇴한 사용자」.
 *   <li>canEdit: 지금 요청한 사람이 수정·삭제할 수 있는지 (작성자 또는 팀장).
 * </ul>
 */
public record CalendarEventResponse(
        Long id,
        String title,
        LocalDateTime startAt,
        LocalDateTime endAt,
        boolean allDay,
        String color,
        String memo,
        CalendarEventAutoType autoType,
        Long createdBy,
        String authorNickname,
        LocalDateTime createdAt,
        boolean canEdit) {

    public static CalendarEventResponse of(
            CalendarEvent event, String authorNickname, boolean canEdit) {
        return new CalendarEventResponse(
                event.getId(),
                event.getTitle(),
                event.getStartAt(),
                event.getEndAt(),
                event.isAllDay(),
                event.getColor(),
                event.getMemo(),
                event.getAutoType(),
                event.getCreatedBy(),
                authorNickname,
                event.getCreatedAt(),
                canEdit);
    }
}
