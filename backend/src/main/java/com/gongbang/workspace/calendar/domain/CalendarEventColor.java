package com.gongbang.workspace.calendar.domain;

import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;

/**
 * 일반 일정 색 5가지 (STYLE_GUIDE 1-3). 기본 BLUE. 자동 일정 전용 RED({@code
 * CalendarService.AUTO_DEADLINE_COLOR})는 사용자가 고를 수 없어 여기에 넣지 않는다.
 */
public enum CalendarEventColor {
    BLUE,
    GREEN,
    YELLOW,
    PURPLE,
    GRAY;

    public static final CalendarEventColor DEFAULT = BLUE;

    /** null이면 기본색, 5색 밖이면(대소문자 다름 포함) 400 INVALID_INPUT. */
    public static CalendarEventColor parseOrDefault(String value) {
        if (value == null) {
            return DEFAULT;
        }
        for (CalendarEventColor color : values()) {
            if (color.name().equals(value)) {
                return color;
            }
        }
        throw new BusinessException(ErrorCode.INVALID_INPUT);
    }
}
