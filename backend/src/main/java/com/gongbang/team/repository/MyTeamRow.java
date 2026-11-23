package com.gongbang.team.repository;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * {@link TeamRepository#findMyTeams} 결과 한 줄 (WS-08 팀 목록 카드). 공모전명·마감일은 모집글이나 값이 없으면 null.
 *
 * <p>native 쿼리 값은 드라이버에 따라 타입이 달라서 받은 그대로 두고 default 메서드에서 바꾼다 ({@link TeamWorkspaceSummary}와 같은
 * 방식).
 */
public interface MyTeamRow {

    Number getTeamId();

    String getTeamName();

    String getContestTitle();

    Object getDeadline();

    Object getLastActivityAt();

    String getMyRole();

    default Long teamId() {
        return getTeamId().longValue();
    }

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

    default LocalDateTime lastActivityAtValue() {
        Object value = getLastActivityAt();
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        throw new IllegalStateException("활동 시각 형식을 알 수 없습니다: " + value.getClass());
    }
}
