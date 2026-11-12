package com.gongbang.contest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.gongbang.contest.domain.Category;
import com.gongbang.contest.domain.Contest;
import com.gongbang.contest.dto.ContestDetailView;
import com.gongbang.contest.repository.ContestRepository;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContestDetailServiceTest {

    private static final ZonedDateTime NOW =
            ZonedDateTime.of(2026, 11, 12, 15, 0, 0, 0, ZoneId.of("Asia/Seoul"));

    @Mock private ContestRepository contestRepository;

    private ContestDetailService contestDetailService;

    @BeforeEach
    void setUp() {
        contestDetailService = new ContestDetailService(contestRepository);
    }

    private void givenContest(Long id, int viewCount) {
        Category category = mock(Category.class);
        given(category.getCode()).willReturn("ETC");
        given(category.getName()).willReturn("기타");
        Contest contest = mock(Contest.class);
        given(contest.getCategory()).willReturn(category);
        given(contest.getEndDate()).willReturn(NOW.toLocalDate().plusDays(10));
        given(contest.getViewCount()).willReturn(viewCount);
        given(contestRepository.findWithCategoryById(id)).willReturn(Optional.of(contest));
    }

    @Test
    @DisplayName("없는 공모전이면 404 CONTEST_NOT_FOUND이고 다시 조회하지 않는다")
    void 없는_공모전이면_404() {
        given(contestRepository.increaseViewCount(99L)).willReturn(0);

        BusinessException e =
                assertThrows(
                        BusinessException.class,
                        () -> contestDetailService.getContest(99L, null, NOW));

        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CONTEST_NOT_FOUND);
        verify(contestRepository, never()).findWithCategoryById(99L);
    }

    @Test
    @DisplayName("오늘 처음 열면 조회수를 올리고 쿠키에 그 번호를 담는다")
    void 오늘_처음_열면_조회수가_오른다() {
        given(contestRepository.increaseViewCount(1L)).willReturn(1);
        givenContest(1L, 8);

        ContestDetailView view = contestDetailService.getContest(1L, "5", NOW);

        verify(contestRepository).increaseViewCount(1L);
        assertThat(view.detail().viewCount()).isEqualTo(8);
        assertThat(view.viewedCookie().getValue()).isEqualTo("5.1");
    }

    @Test
    @DisplayName("오늘 이미 본 공모전이면 조회수를 올리지 않는다")
    void 오늘_이미_본_공모전이면_조회수를_올리지_않는다() {
        givenContest(1L, 8);

        ContestDetailView view = contestDetailService.getContest(1L, "5.1", NOW);

        verify(contestRepository, never()).increaseViewCount(anyLong());
        assertThat(view.detail().viewCount()).isEqualTo(8);
        assertThat(view.viewedCookie().getValue()).isEqualTo("5.1");
    }
}
