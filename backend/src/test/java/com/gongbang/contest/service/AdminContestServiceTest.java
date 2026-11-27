package com.gongbang.contest.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gongbang.contest.domain.*;
import com.gongbang.contest.repository.CategoryRepository;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.service.AdminMemberService;
import com.gongbang.workspace.calendar.service.CalendarService;
import jakarta.persistence.*;
import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AdminContestServiceTest {
    final EntityManager em = mock(EntityManager.class);
    final CategoryRepository categories = mock(CategoryRepository.class);
    final CalendarService calendar = mock(CalendarService.class);
    final AdminContestService service =
            new AdminContestService(
                    org.mockito.Mockito.mock(com.gongbang.admin.service.AdminActivityWriter.class),
                    mock(AdminMemberService.class),
                    categories,
                    calendar,
                    em);

    @Test
    void 확인한마감일이바뀌면_저장하지않는다() {
        Contest c = mock(Contest.class);
        when(c.getEndDate()).thenReturn(LocalDate.of(2026, 12, 1));
        when(em.find(Contest.class, 8L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(c);
        assertThatThrownBy(
                        () ->
                                service.update(
                                        1L,
                                        8L,
                                        new AdminContestService.Update(
                                                "제목",
                                                LocalDate.of(2026, 12, 5),
                                                2L,
                                                LocalDate.of(2026, 11, 30))))
                .isInstanceOf(BusinessException.class);
        verify(c, never()).updateByAdmin(any(), any(), any());
        verifyNoInteractions(calendar);
    }

    @Test
    void 공모전저장후_연결팀자동일정을갱신한다() {
        Contest c = mock(Contest.class);
        Category category = mock(Category.class);
        var oldDate = LocalDate.of(2026, 12, 1);
        var newDate = oldDate.plusDays(7);
        when(c.getEndDate()).thenReturn(oldDate);
        when(c.getCategory()).thenReturn(category);
        when(em.find(Contest.class, 8L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(c);
        when(categories.findById(2L)).thenReturn(Optional.of(category));
        service.update(1L, 8L, new AdminContestService.Update("제목", newDate, 2L, oldDate));
        var order = inOrder(c, em, calendar);
        order.verify(c).updateByAdmin("제목", newDate, category);
        order.verify(em).flush();
        order.verify(calendar).syncContestMilestones(8L);
    }
}
