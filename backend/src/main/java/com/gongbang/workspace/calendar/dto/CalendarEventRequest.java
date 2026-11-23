package com.gongbang.workspace.calendar.dto;

import java.time.LocalDateTime;

/**
 * CAL-04 생성, CAL-05 수정 (수정도 모든 값을 보낸다, 부분 수정 없음). 시각은 시간대 없는 Asia/Seoul 기준 값이다.
 *
 * <p>값 검사는 service가 한다: 제목 1~30자·메모 0~200자(앞뒤 공백을 뺀 뒤 센다, 메모가 공백뿐이면 NULL), 색 5가지(null이면 BLUE), 종일이면
 * 시간을 00:00으로 맞추고 종료일 ≥ 시작일, 시간 일정이면 종료 > 시작. allDay가 없으면(null) 시간 일정이다.
 */
public record CalendarEventRequest(
        String title,
        LocalDateTime startAt,
        LocalDateTime endAt,
        Boolean allDay,
        String color,
        String memo) {

    public boolean allDayOrFalse() {
        return Boolean.TRUE.equals(allDay);
    }
}
