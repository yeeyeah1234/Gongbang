package com.gongbang.workspace.calendar.domain;

/** 자동 일정 종류 (calendar_event.auto_type). 일반 일정은 NULL. 팀마다 종류별로 1개 (UQ team_id·auto_type). */
public enum CalendarEventAutoType {
    /** CAL-06 공모전 접수 마감 */
    CONTEST_DEADLINE
}
