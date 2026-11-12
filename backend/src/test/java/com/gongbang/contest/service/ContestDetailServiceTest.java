package com.gongbang.contest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.gongbang.contest.domain.Category;
import com.gongbang.contest.domain.Contest;
import com.gongbang.contest.dto.ContestDetailResponse;
import com.gongbang.contest.repository.ContestRepository;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContestDetailServiceTest {

    @Mock private ContestRepository contestRepository;

    private ContestDetailService contestDetailService;

    @BeforeEach
    void setUp() {
        contestDetailService = new ContestDetailService(contestRepository);
    }

    @Test
    @DisplayName("없는 공모전이면 404 CONTEST_NOT_FOUND이고 다시 조회하지 않는다")
    void 없는_공모전이면_404() {
        given(contestRepository.increaseViewCount(99L)).willReturn(0);

        BusinessException e =
                assertThrows(BusinessException.class, () -> contestDetailService.getContest(99L));

        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CONTEST_NOT_FOUND);
        verify(contestRepository, never()).findWithCategoryById(99L);
    }

    @Test
    @DisplayName("상세를 열면 조회수를 먼저 올리고, 올린 뒤의 값을 돌려준다")
    void 열면_조회수가_오른다() {
        Category category = mock(Category.class);
        given(category.getCode()).willReturn("ETC");
        given(category.getName()).willReturn("기타");
        Contest contest = mock(Contest.class);
        given(contest.getCategory()).willReturn(category);
        given(contest.getEndDate()).willReturn(LocalDate.now().plusDays(10));
        given(contest.getViewCount()).willReturn(8);
        given(contestRepository.increaseViewCount(1L)).willReturn(1);
        given(contestRepository.findWithCategoryById(1L)).willReturn(Optional.of(contest));

        ContestDetailResponse result = contestDetailService.getContest(1L);

        verify(contestRepository).increaseViewCount(1L);
        assertThat(result.viewCount()).isEqualTo(8);
        assertThat(result.isClosed()).isFalse();
        assertThat(result.isBookmarked()).isFalse();
    }
}
