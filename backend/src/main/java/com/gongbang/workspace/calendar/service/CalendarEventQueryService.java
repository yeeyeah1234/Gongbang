package com.gongbang.workspace.calendar.service;

import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.service.MemberService;
import com.gongbang.team.service.TeamMemberService;
import com.gongbang.workspace.calendar.domain.CalendarEvent;
import com.gongbang.workspace.calendar.dto.CalendarEventResponse;
import com.gongbang.workspace.calendar.repository.CalendarEventRepository;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 팀 캘린더 일정 조회 (CAL-03 목록·상세). 자동 일정도 함께 돌려준다. 모두 현재 팀원만 본다 (403 NOT_TEAM_MEMBER).
 *
 * <p>목록은 기간이 겹치는 일정을 모두 준다: {@code start_at < (to 다음 날 00:00) AND end_at >= from 00:00}. 여러 날 일정이 달
 * 경계에 걸쳐도 나온다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CalendarEventQueryService {

    /** 한 번에 볼 수 있는 기간 (from·to 포함 일수). 월 화면 6주(42일)보다 넉넉하게 잡았다. */
    static final int MAX_RANGE_DAYS = 62;

    /** 회원 행이 없는(created_by NULL) 일반 일정의 표시 이름. MemberService.displayName의 탈퇴 표시와 같다. */
    static final String WITHDRAWN_NAME = "탈퇴한 사용자";

    private final CalendarEventRepository calendarEventRepository;
    private final TeamMemberService teamMemberService;
    private final MemberService memberService;

    /** CAL-03 목록. from > to이거나 기간이 62일을 넘으면 400 INVALID_INPUT. */
    public List<CalendarEventResponse> getEvents(
            Long memberId, Long teamId, LocalDate from, LocalDate to) {
        teamMemberService.requireMember(teamId, memberId);
        if (from == null
                || to == null
                || from.isAfter(to)
                || ChronoUnit.DAYS.between(from, to) + 1 > MAX_RANGE_DAYS) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        List<CalendarEvent> events =
                calendarEventRepository.findOverlapping(
                        teamId, from.atStartOfDay(), to.plusDays(1).atStartOfDay());
        boolean leader = teamMemberService.isLeader(teamId, memberId);
        Map<Long, String> nicknames = new HashMap<>();
        return events.stream()
                .map(
                        event ->
                                CalendarEventResponse.of(
                                        event,
                                        authorNickname(event, nicknames),
                                        event.isEditableBy(memberId, leader)))
                .toList();
    }

    /** CAL-03 상세. 일정을 먼저 찾고(없으면 404) 그 일정의 팀으로 팀원인지 본다. */
    public CalendarEventResponse getEvent(Long memberId, Long eventId) {
        CalendarEvent event =
                calendarEventRepository
                        .findById(eventId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        teamMemberService.requireMember(event.getTeamId(), memberId);
        boolean leader = !event.isAuto() && teamMemberService.isLeader(event.getTeamId(), memberId);
        return CalendarEventResponse.of(
                event,
                authorNickname(event, new HashMap<>()),
                event.isEditableBy(memberId, leader));
    }

    /**
     * 자동 일정은 null, 작성자 회원 행이 없으면 「탈퇴한 사용자」. 같은 작성자는 한 번만 읽는다 (NoticeQueryService.authors와 같은 사정: 회원
     * 여러 명을 한 번에 읽는 메서드가 아직 없다).
     */
    private String authorNickname(CalendarEvent event, Map<Long, String> cache) {
        if (event.isAuto()) {
            return null;
        }
        Long authorId = event.getCreatedBy();
        if (authorId == null) {
            return WITHDRAWN_NAME;
        }
        return cache.computeIfAbsent(authorId, memberService::displayName);
    }
}
