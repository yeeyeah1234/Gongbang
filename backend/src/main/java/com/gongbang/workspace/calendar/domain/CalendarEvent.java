package com.gongbang.workspace.calendar.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 팀 캘린더 일정 (CAL-03, CAL-06). 완전 삭제하는 테이블이다 (AGENTS.md 6).
 *
 * <p>자동 일정(autoType이 있음)은 작성자가 없고(createdBy NULL) 팀마다 종류별로 1개다. 자동 일정은 {@code CalendarService}가
 * 네이티브 upsert로만 만든다. 일반 일정은 {@code CalendarEventService}가 {@link #create}·{@link #update}로 만들고 고친다.
 *
 * <p>종일 일정은 「종료일 포함」 방식이다: 시작·종료 모두 그날 00:00, 하루짜리는 시작 = 종료 (CAL-06 자동 일정과 같음).
 *
 * <p>color는 String이다. 일반 일정은 {@link CalendarEventColor} 5색만 받지만 자동 일정은 RED를 쓰기 때문이다.
 */
@Entity
@Table(name = "calendar_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CalendarEvent {

    public static final int TITLE_MAX_LENGTH = 30;
    public static final int MEMO_MAX_LENGTH = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    @Column(name = "title", nullable = false, length = 30)
    private String title;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    @Column(name = "all_day", nullable = false)
    private boolean allDay;

    @Column(name = "color", nullable = false, length = 10)
    private String color;

    @Column(name = "memo", length = 200)
    private String memo;

    @Column(name = "created_by")
    private Long createdBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "auto_type", length = 30)
    private CalendarEventAutoType autoType;

    // DB가 채우는 공통 컬럼.
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    private CalendarEvent(
            Long teamId,
            Long createdBy,
            String title,
            LocalDateTime startAt,
            LocalDateTime endAt,
            boolean allDay,
            String color,
            String memo) {
        this.teamId = teamId;
        this.createdBy = createdBy;
        this.title = title;
        this.startAt = startAt;
        this.endAt = endAt;
        this.allDay = allDay;
        this.color = color;
        this.memo = memo;
    }

    /** 일반 일정 (autoType NULL). 값 검증과 종일 시간 정리는 service가 끝낸 뒤 부른다. */
    public static CalendarEvent create(
            Long teamId,
            Long createdBy,
            String title,
            LocalDateTime startAt,
            LocalDateTime endAt,
            boolean allDay,
            String color,
            String memo) {
        return new CalendarEvent(teamId, createdBy, title, startAt, endAt, allDay, color, memo);
    }

    /** 값이 하나라도 바뀌었을 때만 고친다. 바뀌었으면 true. */
    public boolean update(
            String title,
            LocalDateTime startAt,
            LocalDateTime endAt,
            boolean allDay,
            String color,
            String memo) {
        if (Objects.equals(this.title, title)
                && Objects.equals(this.startAt, startAt)
                && Objects.equals(this.endAt, endAt)
                && this.allDay == allDay
                && Objects.equals(this.color, color)
                && Objects.equals(this.memo, memo)) {
            return false;
        }
        this.title = title;
        this.startAt = startAt;
        this.endAt = endAt;
        this.allDay = allDay;
        this.color = color;
        this.memo = memo;
        return true;
    }

    /** 자동 일정이면 누구도(팀장 포함) 고치거나 지울 수 없다. */
    public boolean isAuto() {
        return autoType != null;
    }

    /** 이 회원이 고치거나 지울 수 있는지: 일반 일정이면서 작성자 본인이거나 팀장. 현재 팀원인지는 부르는 쪽이 이미 확인했다. */
    public boolean isEditableBy(Long memberId, boolean leader) {
        return !isAuto() && (leader || Objects.equals(createdBy, memberId));
    }
}
