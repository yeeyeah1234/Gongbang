package com.gongbang.workspace.calendar.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gongbang.auth.domain.LoginMember;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.GlobalExceptionHandler;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.domain.MemberRole;
import com.gongbang.workspace.calendar.domain.CalendarEventAutoType;
import com.gongbang.workspace.calendar.dto.CalendarEventRequest;
import com.gongbang.workspace.calendar.dto.CalendarEventResponse;
import com.gongbang.workspace.calendar.service.CalendarEventQueryService;
import com.gongbang.workspace.calendar.service.CalendarEventService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 캘린더 일정 API 경로·파라미터 연결과 상태 코드 (CAL-03~05). 권한·값 검증 자체는 service 테스트가 보고, 여기서는 컨트롤러가 로그인 회원 ID와
 * 경로·쿼리·본문을 service에 그대로 넘기는지, service 예외가 맞는 상태 코드로 나가는지 본다.
 */
@ExtendWith(MockitoExtension.class)
class CalendarEventControllerTest {

    private static final Long MEMBER_ID = 7L;

    @Mock private CalendarEventService calendarEventService;
    @Mock private CalendarEventQueryService calendarEventQueryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(
                                new CalendarEventController(
                                        calendarEventService, calendarEventQueryService))
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                        .build();
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                new LoginMember(MEMBER_ID, MemberRole.USER), null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static final String TIMED_BODY =
            """
            { "title": "회의", "startAt": "2026-11-25T19:00:00", "endAt": "2026-11-25T21:00:00",
              "allDay": false, "color": "GREEN", "memo": "줌 링크" }
            """;

    // ---------- 목록 ----------

    @Test
    @DisplayName("목록: from·to(YYYY-MM-DD)를 LocalDate로 넘기고 일정 배열을 돌려준다")
    void 목록() throws Exception {
        given(
                        calendarEventQueryService.getEvents(
                                MEMBER_ID,
                                3L,
                                LocalDate.of(2026, 11, 1),
                                LocalDate.of(2026, 11, 30)))
                .willReturn(
                        List.of(
                                new CalendarEventResponse(
                                        100L,
                                        "공모전 접수 마감",
                                        LocalDateTime.of(2026, 11, 28, 0, 0),
                                        LocalDateTime.of(2026, 11, 28, 0, 0),
                                        true,
                                        "RED",
                                        null,
                                        CalendarEventAutoType.CONTEST_DEADLINE,
                                        null,
                                        null,
                                        LocalDateTime.of(2026, 11, 20, 10, 0),
                                        false)));

        mockMvc.perform(
                        get("/api/teams/3/events")
                                .param("from", "2026-11-01")
                                .param("to", "2026-11-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                // 시각 직렬화 형식은 Spring Boot ObjectMapper 설정이라 standalone MockMvc에서는 보지 않는다.
                .andExpect(jsonPath("$.data[0].id").value(100))
                .andExpect(jsonPath("$.data[0].autoType").value("CONTEST_DEADLINE"))
                .andExpect(jsonPath("$.data[0].createdBy").doesNotExist())
                .andExpect(jsonPath("$.data[0].canEdit").value(false));
    }

    @Test
    @DisplayName("목록: from이 없거나 날짜 형식이 틀리면 400 INVALID_INPUT이고 service를 부르지 않는다")
    void 목록_파라미터_오류는_400() throws Exception {
        mockMvc.perform(get("/api/teams/3/events").param("to", "2026-11-30"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
        mockMvc.perform(
                        get("/api/teams/3/events")
                                .param("from", "2026/11/01")
                                .param("to", "2026-11-30"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

        verifyNoInteractions(calendarEventQueryService);
    }

    @Test
    @DisplayName("목록: service의 403 NOT_TEAM_MEMBER를 그대로 내보낸다")
    void 목록_팀원_아니면_403() throws Exception {
        given(calendarEventQueryService.getEvents(any(), any(), any(), any()))
                .willThrow(new BusinessException(ErrorCode.NOT_TEAM_MEMBER));

        mockMvc.perform(
                        get("/api/teams/3/events")
                                .param("from", "2026-11-01")
                                .param("to", "2026-11-30"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("NOT_TEAM_MEMBER"));
    }

    // ---------- 생성 ----------

    @Test
    @DisplayName("생성: 본문의 LocalDateTime을 읽어 넘기고 { eventId }를 돌려준다")
    void 생성() throws Exception {
        given(calendarEventService.create(eq(MEMBER_ID), eq(3L), any(CalendarEventRequest.class)))
                .willReturn(55L);

        mockMvc.perform(
                        post("/api/teams/3/events")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(TIMED_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.eventId").value(55));

        ArgumentCaptor<CalendarEventRequest> captor =
                ArgumentCaptor.forClass(CalendarEventRequest.class);
        verify(calendarEventService).create(eq(MEMBER_ID), eq(3L), captor.capture());
        assertThat(captor.getValue())
                .isEqualTo(
                        new CalendarEventRequest(
                                "회의",
                                LocalDateTime.of(2026, 11, 25, 19, 0),
                                LocalDateTime.of(2026, 11, 25, 21, 0),
                                false,
                                "GREEN",
                                "줌 링크"));
    }

    @Test
    @DisplayName("생성: service의 400 INVALID_INPUT을 그대로 내보낸다")
    void 생성_값_오류는_400() throws Exception {
        given(calendarEventService.create(any(), any(), any()))
                .willThrow(new BusinessException(ErrorCode.INVALID_INPUT));

        mockMvc.perform(
                        post("/api/teams/3/events")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(TIMED_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("생성: 시각 형식이 틀린 본문은 400 INVALID_INPUT")
    void 생성_시각_형식_오류는_400() throws Exception {
        mockMvc.perform(
                        post("/api/teams/3/events")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{ \"title\": \"회의\", \"startAt\": \"내일 7시\" }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

        verifyNoInteractions(calendarEventService);
    }

    // ---------- 상세 ----------

    @Test
    @DisplayName("상세: 경로의 일정 ID로 조회한다")
    void 상세() throws Exception {
        mockMvc.perform(get("/api/events/10")).andExpect(status().isOk());

        verify(calendarEventQueryService).getEvent(MEMBER_ID, 10L);
    }

    @Test
    @DisplayName("상세: 없는 일정은 404 RESOURCE_NOT_FOUND")
    void 상세_없으면_404() throws Exception {
        given(calendarEventQueryService.getEvent(MEMBER_ID, 10L))
                .willThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        mockMvc.perform(get("/api/events/10"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    // ---------- 수정 ----------

    @Test
    @DisplayName("수정: 경로의 일정 ID와 본문을 넘기고 { eventId }를 돌려준다")
    void 수정() throws Exception {
        given(calendarEventService.update(eq(MEMBER_ID), eq(10L), any(CalendarEventRequest.class)))
                .willReturn(10L);

        mockMvc.perform(
                        patch("/api/events/10")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(TIMED_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.eventId").value(10));
    }

    @Test
    @DisplayName("수정: 남의 일정은 403 NOT_EVENT_AUTHOR, 자동 일정은 403 AUTO_EVENT_NOT_EDITABLE")
    void 수정_권한_오류는_403() throws Exception {
        given(calendarEventService.update(eq(MEMBER_ID), eq(10L), any()))
                .willThrow(new BusinessException(ErrorCode.NOT_EVENT_AUTHOR));
        given(calendarEventService.update(eq(MEMBER_ID), eq(11L), any()))
                .willThrow(new BusinessException(ErrorCode.AUTO_EVENT_NOT_EDITABLE));

        mockMvc.perform(
                        patch("/api/events/10")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(TIMED_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_EVENT_AUTHOR"));
        mockMvc.perform(
                        patch("/api/events/11")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(TIMED_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AUTO_EVENT_NOT_EDITABLE"));
    }

    // ---------- 삭제 ----------

    @Test
    @DisplayName("삭제: 성공하면 data null")
    void 삭제() throws Exception {
        mockMvc.perform(delete("/api/events/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(calendarEventService).delete(MEMBER_ID, 10L);
    }

    @Test
    @DisplayName("삭제: 자동 일정은 403 AUTO_EVENT_NOT_EDITABLE")
    void 삭제_자동_일정은_403() throws Exception {
        willThrow(new BusinessException(ErrorCode.AUTO_EVENT_NOT_EDITABLE))
                .given(calendarEventService)
                .delete(MEMBER_ID, 10L);

        mockMvc.perform(delete("/api/events/10"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AUTO_EVENT_NOT_EDITABLE"));
    }
}
