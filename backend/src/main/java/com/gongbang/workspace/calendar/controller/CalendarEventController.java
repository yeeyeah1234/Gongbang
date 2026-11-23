package com.gongbang.workspace.calendar.controller;

import com.gongbang.auth.domain.LoginMember;
import com.gongbang.global.common.ApiResponse;
import com.gongbang.workspace.calendar.dto.CalendarEventRequest;
import com.gongbang.workspace.calendar.dto.CalendarEventResponse;
import com.gongbang.workspace.calendar.service.CalendarEventQueryService;
import com.gongbang.workspace.calendar.service.CalendarEventService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 팀 캘린더 일정 API (CAL-03~05). 공지처럼 팀 기준(/teams/{teamId})으로 두고, 일정 하나는 /events/{eventId}로 다룬다. 값 검증과
 * 권한 확인은 service가 한다 (조회·생성 = 팀원, 수정·삭제 = 작성자 또는 팀장, 자동 일정은 누구도 못 바꿈).
 *
 * <p>시각은 시간대 없는 Asia/Seoul 기준 LocalDateTime으로 주고받는다 (예: "2026-11-25T19:00:00").
 */
@RestController
@RequiredArgsConstructor
public class CalendarEventController {

    private final CalendarEventService calendarEventService;
    private final CalendarEventQueryService calendarEventQueryService;

    /** CAL-03 목록: from~to(YYYY-MM-DD, 둘 다 포함, 최대 62일)와 겹치는 일정. 자동 일정 포함. */
    @GetMapping("/api/teams/{teamId}/events")
    public ApiResponse<List<CalendarEventResponse>> getEvents(
            @AuthenticationPrincipal LoginMember me,
            @PathVariable Long teamId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.success(
                calendarEventQueryService.getEvents(me.getMemberId(), teamId, from, to));
    }

    /** CAL-04 생성. */
    @PostMapping("/api/teams/{teamId}/events")
    public ApiResponse<Map<String, Long>> create(
            @AuthenticationPrincipal LoginMember me,
            @PathVariable Long teamId,
            @RequestBody CalendarEventRequest request) {
        return ApiResponse.success(
                Map.of("eventId", calendarEventService.create(me.getMemberId(), teamId, request)));
    }

    /** CAL-03 상세. */
    @GetMapping("/api/events/{eventId}")
    public ApiResponse<CalendarEventResponse> getEvent(
            @AuthenticationPrincipal LoginMember me, @PathVariable Long eventId) {
        return ApiResponse.success(calendarEventQueryService.getEvent(me.getMemberId(), eventId));
    }

    /** CAL-05 수정 (일반 일정만, 모든 값을 보낸다). */
    @PatchMapping("/api/events/{eventId}")
    public ApiResponse<Map<String, Long>> update(
            @AuthenticationPrincipal LoginMember me,
            @PathVariable Long eventId,
            @RequestBody CalendarEventRequest request) {
        return ApiResponse.success(
                Map.of("eventId", calendarEventService.update(me.getMemberId(), eventId, request)));
    }

    /** CAL-05 삭제 (완전 삭제, 일반 일정만). */
    @DeleteMapping("/api/events/{eventId}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal LoginMember me, @PathVariable Long eventId) {
        calendarEventService.delete(me.getMemberId(), eventId);
        return ApiResponse.success();
    }
}
