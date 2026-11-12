package com.gongbang.contest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.gongbang.contest.domain.Category;
import com.gongbang.contest.domain.Contest;
import com.gongbang.contest.domain.ContestSort;
import com.gongbang.contest.domain.ContestStatus;
import com.gongbang.contest.dto.ContestCardResponse;
import com.gongbang.contest.dto.ContestSearchCondition;
import com.gongbang.contest.repository.CategoryRepository;
import com.gongbang.contest.repository.ContestQueryRepository;
import com.gongbang.global.common.PageResponse;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContestQueryServiceTest {

    @Mock private ContestQueryRepository contestQueryRepository;

    @Mock private CategoryRepository categoryRepository;

    private ContestQueryService contestQueryService;

    @BeforeEach
    void setUp() {
        contestQueryService = new ContestQueryService(contestQueryRepository, categoryRepository);
    }

    private Contest contestWithDeadline(LocalDate deadline) {
        Category category = mock(Category.class);
        given(category.getCode()).willReturn("IT_SW");
        given(category.getName()).willReturn("IT/소프트웨어");
        Contest contest = mock(Contest.class);
        given(contest.getEndDate()).willReturn(deadline);
        given(contest.getCategory()).willReturn(category);
        return contest;
    }

    @Test
    @DisplayName("정렬 기준이 latest|deadline|views가 아니면 400 INVALID_SORT")
    void 잘못된_정렬_기준이면_400() {
        BusinessException e =
                assertThrows(
                        BusinessException.class,
                        () ->
                                contestQueryService.getContests(
                                        "active", "popular", null, null, 0, 20));

        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_SORT);
    }

    @Test
    @DisplayName("상태값이 active|all이 아니면 400 INVALID_INPUT")
    void 잘못된_상태값이면_400() {
        BusinessException e =
                assertThrows(
                        BusinessException.class,
                        () ->
                                contestQueryService.getContests(
                                        "closed", "latest", null, null, 0, 20));

        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("page가 음수이거나 size가 범위를 벗어나면 400 INVALID_INPUT")
    void 페이지_범위를_벗어나면_400() {
        int tooBig = ContestQueryService.MAX_PAGE_SIZE + 1;
        for (int[] pageAndSize : new int[][] {{-1, 20}, {0, 0}, {0, tooBig}}) {
            BusinessException e =
                    assertThrows(
                            BusinessException.class,
                            () ->
                                    contestQueryService.getContests(
                                            "active",
                                            "latest",
                                            null,
                                            null,
                                            pageAndSize[0],
                                            pageAndSize[1]));
            assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
        }
    }

    @Test
    @DisplayName("분야·검색어는 앞뒤 공백을 지우고, 비어 있으면 조건을 걸지 않는다")
    void 조회_조건을_정리해서_넘긴다() {
        given(contestQueryRepository.findContests(any(), any(), anyLong(), anyInt()))
                .willReturn(List.of());

        contestQueryService.getContests("all", "deadline", " IT_SW ", "   ", 2, 10);

        ArgumentCaptor<ContestSearchCondition> captor =
                ArgumentCaptor.forClass(ContestSearchCondition.class);
        verify(contestQueryRepository).findContests(captor.capture(), any(), eq(20L), eq(10));
        ContestSearchCondition condition = captor.getValue();
        assertThat(condition.status()).isEqualTo(ContestStatus.ALL);
        assertThat(condition.sort()).isEqualTo(ContestSort.DEADLINE);
        assertThat(condition.categoryCode()).isEqualTo("IT_SW");
        assertThat(condition.keyword()).isNull();
    }

    @Test
    @DisplayName("남은 개수가 있으면 hasNext=true, 마지막 페이지면 false")
    void 다음_페이지_여부를_계산한다() {
        given(contestQueryRepository.findContests(any(), any(), anyLong(), anyInt()))
                .willReturn(List.of());
        given(contestQueryRepository.countContests(any(), any())).willReturn(45L);

        PageResponse<ContestCardResponse> second =
                contestQueryService.getContests("active", "latest", null, null, 1, 20);
        PageResponse<ContestCardResponse> last =
                contestQueryService.getContests("active", "latest", null, null, 2, 20);

        assertThat(second.hasNext()).isTrue();
        assertThat(last.hasNext()).isFalse();
        assertThat(last.totalCount()).isEqualTo(45L);
    }

    @Test
    @DisplayName("마감 당일은 dDay 0이고 아직 마감되지 않았다")
    void 마감_당일은_dDay_0() {
        LocalDate today = LocalDate.of(2026, 11, 12);

        ContestCardResponse card = ContestCardResponse.of(contestWithDeadline(today), today);

        assertThat(card.dDay()).isZero();
        assertThat(card.isClosed()).isFalse();
    }

    @Test
    @DisplayName("마감일이 지나면 isClosed=true, 남았으면 남은 날 수가 dDay")
    void 마감_여부와_남은_날() {
        LocalDate today = LocalDate.of(2026, 11, 12);

        ContestCardResponse closed =
                ContestCardResponse.of(contestWithDeadline(today.minusDays(1)), today);
        ContestCardResponse open =
                ContestCardResponse.of(contestWithDeadline(today.plusDays(14)), today);

        assertThat(closed.isClosed()).isTrue();
        assertThat(open.isClosed()).isFalse();
        assertThat(open.dDay()).isEqualTo(14);
    }
}
