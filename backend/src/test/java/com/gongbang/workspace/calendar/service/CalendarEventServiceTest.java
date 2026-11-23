package com.gongbang.workspace.calendar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.team.service.TeamMemberService;
import com.gongbang.workspace.calendar.domain.CalendarEvent;
import com.gongbang.workspace.calendar.domain.CalendarEventAutoType;
import com.gongbang.workspace.calendar.domain.CalendarEventChangeType;
import com.gongbang.workspace.calendar.domain.CalendarEventChangedEvent;
import com.gongbang.workspace.calendar.dto.CalendarEventRequest;
import com.gongbang.workspace.calendar.repository.CalendarEventRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

/** 권한(작성자·팀장·자동 일정 보호)·값 검증·종일 정리·이벤트를 본다. 실제 저장·기간 겹침 SQL은 CalendarMysqlTest에서 본다. */
@ExtendWith(MockitoExtension.class)
class CalendarEventServiceTest {

    private static final Long TEAM_ID = 1L;
    private static final Long AUTHOR_ID = 10L;
    private static final Long OTHER_ID = 20L;
    private static final Long LEADER_ID = 30L;
    private static final Long EVENT_ID = 500L;

    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 25, 19, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 11, 25, 21, 0);

    @Mock private CalendarEventRepository calendarEventRepository;
    @Mock private TeamMemberService teamMemberService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private CalendarEventService calendarEventService;

    @BeforeEach
    void setUp() {
        calendarEventService =
                new CalendarEventService(
                        calendarEventRepository, teamMemberService, eventPublisher);
    }

    // ---------- 준비 ----------

    private static CalendarEventRequest request(
            String title, LocalDateTime start, LocalDateTime end, Boolean allDay) {
        return new CalendarEventRequest(title, start, end, allDay, null, null);
    }

    private static CalendarEventRequest timed() {
        return request("회의", START, END, false);
    }

    private static CalendarEvent normalEvent(Long createdBy) {
        CalendarEvent event =
                CalendarEvent.create(TEAM_ID, createdBy, "회의", START, END, false, "BLUE", null);
        ReflectionTestUtils.setField(event, "id", EVENT_ID);
        return event;
    }

    private static CalendarEvent autoEvent() {
        CalendarEvent event = normalEvent(null);
        ReflectionTestUtils.setField(event, "autoType", CalendarEventAutoType.CONTEST_DEADLINE);
        ReflectionTestUtils.setField(event, "color", "RED");
        return event;
    }

    private void givenEvent(CalendarEvent event) {
        when(calendarEventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
    }

    /** save가 넘겨받은 일정에 ID를 붙여 돌려주게 한다 (IDENTITY 흉내). */
    private void givenSaveAssignsId() {
        when(calendarEventRepository.save(any(CalendarEvent.class)))
                .thenAnswer(
                        invocation -> {
                            CalendarEvent saved = invocation.getArgument(0);
                            ReflectionTestUtils.setField(saved, "id", EVENT_ID);
                            return saved;
                        });
    }

    private CalendarEvent createAndCaptureSaved(CalendarEventRequest request) {
        givenSaveAssignsId();
        calendarEventService.create(AUTHOR_ID, TEAM_ID, request);
        ArgumentCaptor<CalendarEvent> captor = ArgumentCaptor.forClass(CalendarEvent.class);
        verify(calendarEventRepository).save(captor.capture());
        return captor.getValue();
    }

    private void assertInvalidOnCreate(CalendarEventRequest request) {
        assertThatThrownBy(() -> calendarEventService.create(AUTHOR_ID, TEAM_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
        verify(calendarEventRepository, never()).save(any());
    }

    private static void assertErrorCode(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(expected);
    }

    // ---------- 생성 ----------

    @Test
    @DisplayName("생성: 팀원이면 작성자·값을 그대로 저장하고 CREATED를 발행한다 (색 기본 BLUE)")
    void 생성_성공() {
        CalendarEvent saved = createAndCaptureSaved(timed());

        assertThat(saved.getTeamId()).isEqualTo(TEAM_ID);
        assertThat(saved.getCreatedBy()).isEqualTo(AUTHOR_ID);
        assertThat(saved.getTitle()).isEqualTo("회의");
        assertThat(saved.getStartAt()).isEqualTo(START);
        assertThat(saved.getEndAt()).isEqualTo(END);
        assertThat(saved.isAllDay()).isFalse();
        assertThat(saved.getColor()).isEqualTo("BLUE");
        assertThat(saved.getMemo()).isNull();
        assertThat(saved.getAutoType()).isNull();
        verify(eventPublisher)
                .publishEvent(
                        new CalendarEventChangedEvent(
                                TEAM_ID, EVENT_ID, CalendarEventChangeType.CREATED, START));
    }

    @Test
    @DisplayName("생성: 팀원이 아니면 403 NOT_TEAM_MEMBER이고 저장하지 않는다")
    void 생성_팀원_아니면_403() {
        doThrow(new BusinessException(ErrorCode.NOT_TEAM_MEMBER))
                .when(teamMemberService)
                .requireMember(TEAM_ID, OTHER_ID);

        assertErrorCode(
                () -> calendarEventService.create(OTHER_ID, TEAM_ID, timed()),
                ErrorCode.NOT_TEAM_MEMBER);
        verifyNoInteractions(calendarEventRepository, eventPublisher);
    }

    @Test
    @DisplayName("종일: 시각을 00:00으로 맞추고 종료일을 포함해 저장한다 (11/25~26 → 25일 00:00 ~ 26일 00:00)")
    void 종일_시간을_00시로_맞춘다() {
        CalendarEvent saved =
                createAndCaptureSaved(
                        request(
                                "워크숍",
                                LocalDateTime.of(2026, 11, 25, 9, 30),
                                LocalDateTime.of(2026, 11, 26, 18, 0),
                                true));

        assertThat(saved.isAllDay()).isTrue();
        assertThat(saved.getStartAt()).isEqualTo(LocalDateTime.of(2026, 11, 25, 0, 0));
        assertThat(saved.getEndAt()).isEqualTo(LocalDateTime.of(2026, 11, 26, 0, 0));
    }

    @Test
    @DisplayName("종일: 하루짜리(시작일 = 종료일)는 성공하고 시작 = 종료 00:00으로 저장한다")
    void 하루짜리_종일은_성공() {
        CalendarEvent saved =
                createAndCaptureSaved(
                        request(
                                "발표",
                                LocalDateTime.of(2026, 11, 25, 0, 0),
                                LocalDateTime.of(2026, 11, 25, 0, 0),
                                true));

        assertThat(saved.getStartAt()).isEqualTo(LocalDateTime.of(2026, 11, 25, 0, 0));
        assertThat(saved.getEndAt()).isEqualTo(saved.getStartAt());
    }

    @Test
    @DisplayName("종일: 종료일이 시작일보다 앞이면 400")
    void 종일_종료일이_앞이면_400() {
        assertInvalidOnCreate(
                request(
                        "워크숍",
                        LocalDateTime.of(2026, 11, 26, 0, 0),
                        LocalDateTime.of(2026, 11, 25, 23, 0),
                        true));
    }

    @Test
    @DisplayName("시간 일정: 종료 = 시작이면 400")
    void 시간_일정_종료가_시작과_같으면_400() {
        assertInvalidOnCreate(request("회의", START, START, false));
    }

    @Test
    @DisplayName("시간 일정: 종료가 시작보다 앞이면 400, allDay가 없으면(null) 시간 일정으로 본다")
    void 시간_일정_종료가_앞이면_400() {
        assertInvalidOnCreate(request("회의", END, START, null));
    }

    @Test
    @DisplayName("시작·종료 시각이 없으면 400")
    void 시각이_없으면_400() {
        assertInvalidOnCreate(request("회의", null, END, false));
        assertInvalidOnCreate(request("회의", START, null, true));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\n\t"})
    @DisplayName("제목이 비었거나 공백뿐이면 400")
    void 제목이_비면_400(String title) {
        assertInvalidOnCreate(request(title, START, END, false));
    }

    @Test
    @DisplayName("제목: 앞뒤 공백을 빼고 30자(코드포인트)까지 저장한다")
    void 제목_30자는_저장() {
        String thirtyEmoji = "😀".repeat(30); // UTF-16으로는 60자지만 문자는 30개
        CalendarEvent saved =
                createAndCaptureSaved(request("  " + thirtyEmoji + "  ", START, END, false));
        assertThat(saved.getTitle()).isEqualTo(thirtyEmoji);
    }

    @Test
    @DisplayName("제목: 31자면 400")
    void 제목_31자는_400() {
        assertInvalidOnCreate(request("가".repeat(31), START, END, false));
    }

    @Test
    @DisplayName("메모: 공백뿐이면 NULL, 앞뒤 공백은 빼고 저장")
    void 메모_공백은_null() {
        CalendarEvent blank =
                createAndCaptureSaved(
                        new CalendarEventRequest("회의", START, END, false, null, "  \n "));
        assertThat(blank.getMemo()).isNull();
    }

    @Test
    @DisplayName("메모: 앞뒤 공백을 빼고 200자까지 저장한다")
    void 메모_200자는_저장() {
        CalendarEvent saved =
                createAndCaptureSaved(
                        new CalendarEventRequest(
                                "회의", START, END, false, null, " " + "가".repeat(200) + " "));
        assertThat(saved.getMemo()).isEqualTo("가".repeat(200));
    }

    @Test
    @DisplayName("메모: 201자면 400")
    void 메모_201자는_400() {
        assertInvalidOnCreate(
                new CalendarEventRequest("회의", START, END, false, null, "가".repeat(201)));
    }

    @Test
    @DisplayName("색: 5색은 그대로 저장한다")
    void 색_5색은_저장() {
        CalendarEvent saved =
                createAndCaptureSaved(
                        new CalendarEventRequest("회의", START, END, false, "PURPLE", null));
        assertThat(saved.getColor()).isEqualTo("PURPLE");
    }

    @ParameterizedTest
    @ValueSource(strings = {"RED", "blue", "", "PINK"})
    @DisplayName("색: 5색 밖(자동 일정용 RED, 소문자, 빈 값 포함)이면 400")
    void 색_5색_밖이면_400(String color) {
        assertInvalidOnCreate(new CalendarEventRequest("회의", START, END, false, color, null));
    }

    // ---------- 수정 ----------

    @Test
    @DisplayName("수정: 작성자는 고칠 수 있고 바뀌면 UPDATED를 새 시작 시각으로 발행한다")
    void 작성자는_수정할_수_있다() {
        CalendarEvent event = normalEvent(AUTHOR_ID);
        givenEvent(event);
        LocalDateTime newStart = START.plusDays(1);

        Long id =
                calendarEventService.update(
                        AUTHOR_ID,
                        EVENT_ID,
                        new CalendarEventRequest(
                                "회의 변경", newStart, END.plusDays(1), false, "GREEN", "메모"));

        assertThat(id).isEqualTo(EVENT_ID);
        assertThat(event.getTitle()).isEqualTo("회의 변경");
        assertThat(event.getColor()).isEqualTo("GREEN");
        assertThat(event.getMemo()).isEqualTo("메모");
        verify(teamMemberService, never()).isLeader(anyLong(), anyLong());
        verify(eventPublisher)
                .publishEvent(
                        new CalendarEventChangedEvent(
                                TEAM_ID, EVENT_ID, CalendarEventChangeType.UPDATED, newStart));
    }

    @Test
    @DisplayName("수정: 바뀐 값이 없으면 UPDATED를 발행하지 않는다")
    void 바뀐_것이_없으면_발행하지_않는다() {
        givenEvent(normalEvent(AUTHOR_ID));

        calendarEventService.update(AUTHOR_ID, EVENT_ID, timed());

        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("수정: 다른 팀원 일정이면 403 NOT_EVENT_AUTHOR이고 값이 그대로다")
    void 다른_팀원_일정_수정은_403() {
        CalendarEvent event = normalEvent(AUTHOR_ID);
        givenEvent(event);

        assertErrorCode(
                () ->
                        calendarEventService.update(
                                OTHER_ID, EVENT_ID, request("바꿈", START, END, false)),
                ErrorCode.NOT_EVENT_AUTHOR);
        assertThat(event.getTitle()).isEqualTo("회의");
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("수정: 팀장은 남의 일정을 고칠 수 있다")
    void 팀장은_남의_일정을_수정할_수_있다() {
        CalendarEvent event = normalEvent(AUTHOR_ID);
        givenEvent(event);
        when(teamMemberService.isLeader(TEAM_ID, LEADER_ID)).thenReturn(true);

        calendarEventService.update(LEADER_ID, EVENT_ID, request("팀장 수정", START, END, false));

        assertThat(event.getTitle()).isEqualTo("팀장 수정");
    }

    @Test
    @DisplayName("수정: 작성자 회원이 지워진(created_by NULL) 일정은 팀장만 고칠 수 있다")
    void 작성자_없는_일정은_팀장만() {
        givenEvent(normalEvent(null));

        assertErrorCode(
                () -> calendarEventService.update(OTHER_ID, EVENT_ID, timed()),
                ErrorCode.NOT_EVENT_AUTHOR);
    }

    @Test
    @DisplayName("수정: 자동 일정은 팀장도 403 AUTO_EVENT_NOT_EDITABLE")
    void 자동_일정은_팀장도_수정할_수_없다() {
        givenEvent(autoEvent());

        assertErrorCode(
                () -> calendarEventService.update(LEADER_ID, EVENT_ID, timed()),
                ErrorCode.AUTO_EVENT_NOT_EDITABLE);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("수정: 팀을 나간 작성자는 403 NOT_TEAM_MEMBER")
    void 나간_작성자는_403() {
        givenEvent(normalEvent(AUTHOR_ID));
        doThrow(new BusinessException(ErrorCode.NOT_TEAM_MEMBER))
                .when(teamMemberService)
                .requireMember(TEAM_ID, AUTHOR_ID);

        assertErrorCode(
                () -> calendarEventService.update(AUTHOR_ID, EVENT_ID, timed()),
                ErrorCode.NOT_TEAM_MEMBER);
    }

    @Test
    @DisplayName("수정: 권한이 있어도 값이 틀리면 400이고 고치지 않는다")
    void 수정_값이_틀리면_400() {
        CalendarEvent event = normalEvent(AUTHOR_ID);
        givenEvent(event);

        assertErrorCode(
                () ->
                        calendarEventService.update(
                                AUTHOR_ID, EVENT_ID, request("회의", END, START, false)),
                ErrorCode.INVALID_INPUT);
        assertThat(event.getStartAt()).isEqualTo(START);
    }

    @Test
    @DisplayName("수정·삭제: 없는 일정이면 404 RESOURCE_NOT_FOUND")
    void 없는_일정은_404() {
        when(calendarEventRepository.findById(EVENT_ID)).thenReturn(Optional.empty());

        assertErrorCode(
                () -> calendarEventService.update(AUTHOR_ID, EVENT_ID, timed()),
                ErrorCode.RESOURCE_NOT_FOUND);
        assertErrorCode(
                () -> calendarEventService.delete(AUTHOR_ID, EVENT_ID),
                ErrorCode.RESOURCE_NOT_FOUND);
    }

    // ---------- 삭제 ----------

    @Test
    @DisplayName("삭제: 작성자는 지울 수 있고 DELETED를 지운 일정의 시작 시각으로 발행한다")
    void 작성자는_삭제할_수_있다() {
        CalendarEvent event = normalEvent(AUTHOR_ID);
        givenEvent(event);

        calendarEventService.delete(AUTHOR_ID, EVENT_ID);

        verify(calendarEventRepository).delete(event);
        verify(eventPublisher)
                .publishEvent(
                        new CalendarEventChangedEvent(
                                TEAM_ID, EVENT_ID, CalendarEventChangeType.DELETED, START));
    }

    @Test
    @DisplayName("삭제: 팀장은 남의 일정을 지울 수 있다")
    void 팀장은_남의_일정을_삭제할_수_있다() {
        CalendarEvent event = normalEvent(AUTHOR_ID);
        givenEvent(event);
        when(teamMemberService.isLeader(TEAM_ID, LEADER_ID)).thenReturn(true);

        calendarEventService.delete(LEADER_ID, EVENT_ID);

        verify(calendarEventRepository).delete(event);
    }

    @Test
    @DisplayName("삭제: 다른 팀원 일정이면 403 NOT_EVENT_AUTHOR")
    void 다른_팀원_일정_삭제는_403() {
        givenEvent(normalEvent(AUTHOR_ID));

        assertErrorCode(
                () -> calendarEventService.delete(OTHER_ID, EVENT_ID), ErrorCode.NOT_EVENT_AUTHOR);
        verify(calendarEventRepository, never()).delete(any());
    }

    @Test
    @DisplayName("삭제: 자동 일정은 팀장도 403 AUTO_EVENT_NOT_EDITABLE")
    void 자동_일정은_팀장도_삭제할_수_없다() {
        givenEvent(autoEvent());

        assertErrorCode(
                () -> calendarEventService.delete(LEADER_ID, EVENT_ID),
                ErrorCode.AUTO_EVENT_NOT_EDITABLE);
        verify(calendarEventRepository, never()).delete(any());
        verifyNoInteractions(eventPublisher);
    }
}
