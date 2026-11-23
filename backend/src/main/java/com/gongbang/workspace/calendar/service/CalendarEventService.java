package com.gongbang.workspace.calendar.service;

import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.team.service.TeamMemberService;
import com.gongbang.workspace.calendar.domain.CalendarEvent;
import com.gongbang.workspace.calendar.domain.CalendarEventChangeType;
import com.gongbang.workspace.calendar.domain.CalendarEventChangedEvent;
import com.gongbang.workspace.calendar.domain.CalendarEventColor;
import com.gongbang.workspace.calendar.dto.CalendarEventRequest;
import com.gongbang.workspace.calendar.repository.CalendarEventRepository;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 팀 캘린더 일반 일정 생성·수정·삭제 (CAL-04, CAL-05). 조회는 {@link CalendarEventQueryService}, 자동 일정(CAL-06)은
 * {@link CalendarService}.
 *
 * <p>권한: 생성은 현재 팀원(403 NOT_TEAM_MEMBER). 수정·삭제는 일정을 먼저 찾고(없으면 404 RESOURCE_NOT_FOUND) 그 팀의 현재 팀원인지
 * 본 뒤, 자동 일정이면 팀장이라도 403 AUTO_EVENT_NOT_EDITABLE, 작성자도 팀장도 아니면 403 NOT_EVENT_AUTHOR.
 *
 * <p>값이 틀리면 모두 400 INVALID_INPUT (공지와 같음). 동시 수정은 버전 검사 없이 나중 저장이 남는다.
 *
 * <p>이벤트는 트랜잭션 안에서 발행한다. 받는 쪽은 AFTER_COMMIT으로 받는다 ({@link CalendarEventChangedEvent}).
 */
@Service
@RequiredArgsConstructor
public class CalendarEventService {

    private final CalendarEventRepository calendarEventRepository;
    private final TeamMemberService teamMemberService;
    private final ApplicationEventPublisher eventPublisher;

    /** CAL-04 생성. 새 일정 ID를 돌려준다. */
    @Transactional
    public Long create(Long memberId, Long teamId, CalendarEventRequest request) {
        teamMemberService.requireMember(teamId, memberId);
        EventValues values = EventValues.from(request);

        CalendarEvent event =
                calendarEventRepository.save(
                        CalendarEvent.create(
                                teamId,
                                memberId,
                                values.title(),
                                values.startAt(),
                                values.endAt(),
                                values.allDay(),
                                values.color(),
                                values.memo()));

        publish(event, CalendarEventChangeType.CREATED);
        return event.getId();
    }

    /** CAL-05 수정. 값이 실제로 바뀌었을 때만 UPDATED를 발행한다. */
    @Transactional
    public Long update(Long memberId, Long eventId, CalendarEventRequest request) {
        CalendarEvent event = findEvent(eventId);
        requireEditable(event, memberId);
        EventValues values = EventValues.from(request);

        boolean changed =
                event.update(
                        values.title(),
                        values.startAt(),
                        values.endAt(),
                        values.allDay(),
                        values.color(),
                        values.memo());
        if (changed) {
            publish(event, CalendarEventChangeType.UPDATED);
        }
        return event.getId();
    }

    /** CAL-05 삭제. 행을 지운다 (완전 삭제, AGENTS.md 6). */
    @Transactional
    public void delete(Long memberId, Long eventId) {
        CalendarEvent event = findEvent(eventId);
        requireEditable(event, memberId);
        calendarEventRepository.delete(event);
        publish(event, CalendarEventChangeType.DELETED);
    }

    // ---------- 권한 ----------

    private CalendarEvent findEvent(Long eventId) {
        return calendarEventRepository
                .findById(eventId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    /** 현재 팀원 → 자동 일정 아님 → 작성자 또는 팀장 순서로 본다. 작성자 본인이면 팀장 여부를 읽지 않는다. */
    private void requireEditable(CalendarEvent event, Long memberId) {
        teamMemberService.requireMember(event.getTeamId(), memberId);
        if (event.isAuto()) {
            throw new BusinessException(ErrorCode.AUTO_EVENT_NOT_EDITABLE);
        }
        boolean author = Objects.equals(event.getCreatedBy(), memberId);
        if (!author && !teamMemberService.isLeader(event.getTeamId(), memberId)) {
            throw new BusinessException(ErrorCode.NOT_EVENT_AUTHOR);
        }
    }

    private void publish(CalendarEvent event, CalendarEventChangeType type) {
        eventPublisher.publishEvent(
                new CalendarEventChangedEvent(
                        event.getTeamId(), event.getId(), type, event.getStartAt()));
    }

    // ---------- 값 검증 ----------

    /** 검증과 정리(strip, 종일 00:00, 기본색)를 끝낸 저장할 값. */
    record EventValues(
            String title,
            LocalDateTime startAt,
            LocalDateTime endAt,
            boolean allDay,
            String color,
            String memo) {

        static EventValues from(CalendarEventRequest request) {
            String title = requireText(request.title(), CalendarEvent.TITLE_MAX_LENGTH);
            String memo = optionalText(request.memo(), CalendarEvent.MEMO_MAX_LENGTH);
            String color = CalendarEventColor.parseOrDefault(request.color()).name();
            if (request.startAt() == null || request.endAt() == null) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }

            boolean allDay = request.allDayOrFalse();
            LocalDateTime startAt = request.startAt();
            LocalDateTime endAt = request.endAt();
            if (allDay) {
                // 종일은 「종료일 포함」: 둘 다 그날 00:00, 하루짜리는 시작 = 종료.
                startAt = startAt.toLocalDate().atStartOfDay();
                endAt = endAt.toLocalDate().atStartOfDay();
                if (endAt.isBefore(startAt)) {
                    throw new BusinessException(ErrorCode.INVALID_INPUT);
                }
            } else if (!endAt.isAfter(startAt)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
            return new EventValues(title, startAt, endAt, allDay, color, memo);
        }
    }

    /**
     * 앞뒤 공백을 뺀 값이 1자 이상, max자 이하인지 본다. 글자 수는 DB(utf8mb4)처럼 문자(코드포인트) 단위로 센다 ({@code
     * NoticeService.requireText}와 같은 규칙).
     */
    static String requireText(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return requireMaxLength(value.strip(), maxLength);
    }

    /** 비었거나 공백뿐이면 null, 아니면 앞뒤 공백을 뺀 값 (max자 이하). */
    static String optionalText(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return requireMaxLength(value.strip(), maxLength);
    }

    private static String requireMaxLength(String stripped, int maxLength) {
        if (stripped.codePointCount(0, stripped.length()) > maxLength) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return stripped;
    }
}
