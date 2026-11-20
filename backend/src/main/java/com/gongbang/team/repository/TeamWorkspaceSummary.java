package com.gongbang.team.repository;

import java.sql.Date;
import java.time.LocalDate;

/** {@link TeamRepository#findWorkspaceSummary} 결과 한 줄 (WS-02 헤더). 공모전명·마감일은 모집글이나 값이 없으면 null. */
public interface TeamWorkspaceSummary {

    String getTeamName();

    String getContestTitle();

    /**
     * native 쿼리의 DATE 값은 드라이버·Hibernate 설정에 따라 java.sql.Date나 LocalDate로 온다. 자동 변환(java.util.Date →
     * LocalDate)은 java.sql.Date에서 예외가 나므로 받은 그대로 두고 {@link #deadlineDate()}에서 바꾼다.
     */
    Object getDeadline();

    default LocalDate deadlineDate() {
        Object value = getDeadline();
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate date) {
            return date;
        }
        if (value instanceof Date date) {
            return date.toLocalDate();
        }
        throw new IllegalStateException("마감일 형식을 알 수 없습니다: " + value.getClass());
    }
}
