package com.gongbang.workspace.calendar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.service.MemberService;
import com.gongbang.team.service.TeamMemberService;
import com.gongbang.workspace.calendar.domain.CalendarEvent;
import com.gongbang.workspace.calendar.domain.CalendarEventAutoType;
import com.gongbang.workspace.calendar.dto.CalendarEventResponse;
import com.gongbang.workspace.calendar.repository.CalendarEventRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** 기간 검증·조회 경계·canEdit·작성자 표시를 본다. 기간 겹침 SQL 자체는 CalendarMysqlTest에서 본다. */
@ExtendWith(MockitoExtension.class)
class CalendarEventQueryServiceTest {

    private static final Long TEAM_ID = 1L;
    private static final Long ME = 10L;
    private static final Long OTHER_ID = 20L;

    private static final LocalDate NOV_1 = LocalDate.of(2026, 11, 1);
    private static final LocalDate NOV_30 = LocalDate.of(2026, 11, 30);

    @Mock private CalendarEventRepository calendarEventRepository;
    @Mock private TeamMemberService teamMemberService;
    @Mock private MemberService memberService;

    private CalendarEventQueryService calendarEventQueryService;

    @BeforeEach
    void setUp() {
        calendarEventQueryService =
                new CalendarEventQueryService(
                        calendarEventRepository, teamMemberService, memberService);
    }

    private static CalendarEvent normalEvent(Long id, Long createdBy) {
        CalendarEvent event =
                CalendarEvent.create(
                        TEAM_ID,
                        createdBy,
                        "회의",
                        LocalDateTime.of(2026, 11, 25, 19, 0),
                        LocalDateTime.of(2026, 11, 25, 21, 0),
                        false,
                        "BLUE",
                        null);
        ReflectionTestUtils.setField(event, "id", id);
        return event;
    }

    private static CalendarEvent autoEvent(Long id) {
        CalendarEvent event = normalEvent(id, null);
        ReflectionTestUtils.setField(event, "autoType", CalendarEventAutoType.CONTEST_DEADLINE);
        ReflectionTestUtils.setField(event, "color", "RED");
        ReflectionTestUtils.setField(event, "allDay", true);
        return event;
    }

    private void givenEvents(CalendarEvent... events) {
        when(calendarEventRepository.findOverlapping(any(), any(), any()))
                .thenReturn(List.of(events));
    }

    private void assertInvalidRange(LocalDate from, LocalDate to) {
        assertThatThrownBy(() -> calendarEventQueryService.getEvents(ME, TEAM_ID, from, to))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    // ---------- 목록 기간 ----------

    @Test
    @DisplayName("목록: from 00:00 이상, to 다음 날 00:00 미만으로 조회한다")
    void 조회_경계를_넘긴다() {
        givenEvents();

        calendarEventQueryService.getEvents(ME, TEAM_ID, NOV_1, NOV_30);

        verify(calendarEventRepository)
                .findOverlapping(
                        TEAM_ID,
                        LocalDateTime.of(2026, 11, 1, 0, 0),
                        LocalDateTime.of(2026, 12, 1, 0, 0));
    }

    @Test
    @DisplayName("목록: from = to(하루)와 62일 기간은 성공한다")
    void 하루와_62일은_성공() {
        givenEvents();

        calendarEventQueryService.getEvents(ME, TEAM_ID, NOV_1, NOV_1);
        calendarEventQueryService.getEvents(ME, TEAM_ID, NOV_1, NOV_1.plusDays(61));

        verify(calendarEventRepository, times(2)).findOverlapping(any(), any(), any());
    }

    @Test
    @DisplayName("목록: from > to이거나 63일이면 400이고 조회하지 않는다")
    void 기간이_틀리면_400() {
        assertInvalidRange(NOV_30, NOV_1);
        assertInvalidRange(NOV_1, NOV_1.plusDays(62));
        verify(calendarEventRepository, never()).findOverlapping(any(), any(), any());
    }

    @Test
    @DisplayName("목록: 팀원이 아니면 403 NOT_TEAM_MEMBER이고 조회하지 않는다")
    void 목록_팀원_아니면_403() {
        doThrow(new BusinessException(ErrorCode.NOT_TEAM_MEMBER))
                .when(teamMemberService)
                .requireMember(TEAM_ID, ME);

        assertThatThrownBy(() -> calendarEventQueryService.getEvents(ME, TEAM_ID, NOV_1, NOV_30))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_TEAM_MEMBER);
        verify(calendarEventRepository, never()).findOverlapping(any(), any(), any());
    }

    // ---------- canEdit·작성자 ----------

    @Test
    @DisplayName("목록(팀원): 내 일정만 canEdit true, 자동 일정은 false·작성자 null, 같은 작성자 닉네임은 한 번만 읽는다")
    void 팀원의_canEdit과_작성자() {
        givenEvents(
                normalEvent(1L, ME),
                normalEvent(2L, OTHER_ID),
                normalEvent(3L, OTHER_ID),
                autoEvent(4L));
        when(memberService.displayName(ME)).thenReturn("나");
        when(memberService.displayName(OTHER_ID)).thenReturn("다른사람");

        List<CalendarEventResponse> result =
                calendarEventQueryService.getEvents(ME, TEAM_ID, NOV_1, NOV_30);

        assertThat(result).extracting(CalendarEventResponse::id).containsExactly(1L, 2L, 3L, 4L);
        assertThat(result)
                .extracting(CalendarEventResponse::canEdit)
                .containsExactly(true, false, false, false);
        assertThat(result)
                .extracting(CalendarEventResponse::authorNickname)
                .containsExactly("나", "다른사람", "다른사람", null);

        CalendarEventResponse auto = result.get(3);
        assertThat(auto.autoType()).isEqualTo(CalendarEventAutoType.CONTEST_DEADLINE);
        assertThat(auto.createdBy()).isNull();
        assertThat(auto.color()).isEqualTo("RED");
        verify(memberService, times(1)).displayName(OTHER_ID);
        verify(teamMemberService, times(1)).isLeader(TEAM_ID, ME);
    }

    @Test
    @DisplayName("목록(팀장): 남의 일정도 canEdit true, 자동 일정은 팀장도 false")
    void 팀장의_canEdit() {
        givenEvents(normalEvent(1L, OTHER_ID), autoEvent(2L));
        when(teamMemberService.isLeader(TEAM_ID, ME)).thenReturn(true);
        when(memberService.displayName(OTHER_ID)).thenReturn("다른사람");

        List<CalendarEventResponse> result =
                calendarEventQueryService.getEvents(ME, TEAM_ID, NOV_1, NOV_30);

        assertThat(result).extracting(CalendarEventResponse::canEdit).containsExactly(true, false);
    }

    @Test
    @DisplayName("작성자 회원 행이 없는(created_by NULL) 일반 일정은 「탈퇴한 사용자」")
    void 작성자_없는_일반_일정() {
        givenEvents(normalEvent(1L, null));

        List<CalendarEventResponse> result =
                calendarEventQueryService.getEvents(ME, TEAM_ID, NOV_1, NOV_30);

        assertThat(result.get(0).authorNickname())
                .isEqualTo(CalendarEventQueryService.WITHDRAWN_NAME);
        assertThat(result.get(0).createdBy()).isNull();
        assertThat(result.get(0).canEdit()).isFalse();
        verify(memberService, never()).displayName(anyLong());
    }

    // ---------- 상세 ----------

    @Test
    @DisplayName("상세: 그 일정 팀의 팀원이면 돌려준다 (내 일정이면 canEdit true)")
    void 상세_성공() {
        when(calendarEventRepository.findById(1L)).thenReturn(Optional.of(normalEvent(1L, ME)));
        when(memberService.displayName(ME)).thenReturn("나");

        CalendarEventResponse response = calendarEventQueryService.getEvent(ME, 1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.title()).isEqualTo("회의");
        assertThat(response.authorNickname()).isEqualTo("나");
        assertThat(response.canEdit()).isTrue();
    }

    @Test
    @DisplayName("상세: 자동 일정은 canEdit false이고 팀장 여부를 읽지 않는다")
    void 상세_자동_일정() {
        when(calendarEventRepository.findById(4L)).thenReturn(Optional.of(autoEvent(4L)));

        CalendarEventResponse response = calendarEventQueryService.getEvent(ME, 4L);

        assertThat(response.canEdit()).isFalse();
        assertThat(response.authorNickname()).isNull();
        verify(teamMemberService, never()).isLeader(anyLong(), anyLong());
    }

    @Test
    @DisplayName("상세: 없는 일정은 404, 다른 팀 일정은 403 NOT_TEAM_MEMBER")
    void 상세_404와_403() {
        when(calendarEventRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> calendarEventQueryService.getEvent(ME, 99L))
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);

        when(calendarEventRepository.findById(1L))
                .thenReturn(Optional.of(normalEvent(1L, OTHER_ID)));
        doThrow(new BusinessException(ErrorCode.NOT_TEAM_MEMBER))
                .when(teamMemberService)
                .requireMember(TEAM_ID, ME);
        assertThatThrownBy(() -> calendarEventQueryService.getEvent(ME, 1L))
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_TEAM_MEMBER);
    }
}
