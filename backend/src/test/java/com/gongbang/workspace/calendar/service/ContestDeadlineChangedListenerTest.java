package com.gongbang.workspace.calendar.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import com.gongbang.contest.domain.ContestDeadlineChangedEvent;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContestDeadlineChangedListenerTest {

    @Mock private CalendarService calendarService;

    private ContestDeadlineChangedListener listener;

    @BeforeEach
    void setUp() {
        listener = new ContestDeadlineChangedListener(calendarService);
    }

    @Test
    @DisplayName("이벤트의 contestId로 팀 자동 일정 갱신을 부른다 (CAL-06)")
    void 이벤트의_contestId로_서비스를_부른다() {
        listener.handle(new ContestDeadlineChangedEvent(7L, LocalDate.of(2026, 12, 31)));

        verify(calendarService).upsertAutoEventsByContest(7L);
    }

    @Test
    @DisplayName("서비스가 실패해도 예외를 밖으로 던지지 않는다 (배치에 영향 없음)")
    void 서비스가_실패해도_예외를_던지지_않는다() {
        willThrow(new IllegalStateException("DB 오류"))
                .given(calendarService)
                .upsertAutoEventsByContest(7L);

        assertDoesNotThrow(
                () ->
                        listener.handle(
                                new ContestDeadlineChangedEvent(7L, LocalDate.of(2026, 12, 31))));
    }
}
