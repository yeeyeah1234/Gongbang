package com.gongbang.workspace.calendar.service;

import static org.mockito.Mockito.verify;

import com.gongbang.workspace.calendar.repository.CalendarEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 자동 일정 값이 쿼리에 그대로 넘어가는지만 본다. 쿼리 의미(마감일 계산, upsert)는 CalendarMysqlTest에서 본다. */
@ExtendWith(MockitoExtension.class)
class CalendarServiceTest {

    @Mock private CalendarEventRepository calendarEventRepository;

    private CalendarService calendarService;

    @BeforeEach
    void setUp() {
        calendarService = new CalendarService(calendarEventRepository);
    }

    @Test
    @DisplayName("팀 자동 일정은 「공모전 접수 마감」·RED·CONTEST_DEADLINE으로 upsert한다 (CAL-06)")
    void 팀_자동_일정_값() {
        calendarService.upsertAutoEvent(3L);

        verify(calendarEventRepository)
                .upsertContestDeadlineForTeam(3L, "공모전 접수 마감", "RED", "CONTEST_DEADLINE");
    }

    @Test
    @DisplayName("공모전 기준 자동 일정도 같은 값으로 upsert한다 (CAL-06)")
    void 공모전_기준_자동_일정_값() {
        calendarService.upsertAutoEventsByContest(5L);

        verify(calendarEventRepository)
                .upsertContestDeadlineForContest(5L, "공모전 접수 마감", "RED", "CONTEST_DEADLINE");
    }
}
