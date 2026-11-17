package com.gongbang.contest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.gongbang.contest.domain.Category;
import com.gongbang.contest.domain.Contest;
import com.gongbang.contest.domain.ContestSearchSort;
import com.gongbang.contest.domain.ContestSort;
import com.gongbang.contest.domain.ContestStatus;
import com.gongbang.contest.dto.ContestCardResponse;
import com.gongbang.contest.dto.ContestSearchCondition;
import com.gongbang.contest.repository.CategoryRepository;
import com.gongbang.contest.repository.ContestQueryRepository;
import com.gongbang.global.common.PageResponse;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.service.BookmarkService;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
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

    @Mock private BookmarkService bookmarkService;

    private ContestQueryService contestQueryService;

    @BeforeEach
    void setUp() {
        contestQueryService =
                new ContestQueryService(
                        contestQueryRepository, categoryRepository, bookmarkService);
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
                                        "active", "popular", null, null, 0, 20, null));

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
                                        "closed", "latest", null, null, 0, 20, null));

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
                                            pageAndSize[1],
                                            null));
            assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
        }
    }

    @Test
    @DisplayName("분야·검색어는 앞뒤 공백을 지우고, 비어 있으면 조건을 걸지 않는다")
    void 조회_조건을_정리해서_넘긴다() {
        given(contestQueryRepository.findContests(any(), any(), anyLong(), anyInt()))
                .willReturn(List.of());

        contestQueryService.getContests("all", "deadline", " IT_SW ", "   ", 2, 10, null);

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
                contestQueryService.getContests("active", "latest", null, null, 1, 20, null);
        PageResponse<ContestCardResponse> last =
                contestQueryService.getContests("active", "latest", null, null, 2, 20, null);

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

    @Test
    @DisplayName("통합 검색 정렬은 accuracy|latest|deadline만 받고, 그 밖이면 400 INVALID_SORT")
    void 검색_정렬이_잘못되면_400() {
        BusinessException e =
                assertThrows(
                        BusinessException.class,
                        () ->
                                contestQueryService.searchContests(
                                        "공모전", "views", false, 0, 20, null));

        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_SORT);
    }

    @Test
    @DisplayName("통합 검색은 기본으로 진행 중만, 「마감된 공모전 포함」이면 마감된 것까지 찾는다")
    void 마감된_공모전_포함_여부() {
        given(contestQueryRepository.searchContests(any(), any(), any(), anyLong(), anyInt()))
                .willReturn(List.of());
        ArgumentCaptor<ContestSearchCondition> captor =
                ArgumentCaptor.forClass(ContestSearchCondition.class);

        contestQueryService.searchContests("공모전", "accuracy", false, 0, 20, null);
        contestQueryService.searchContests("공모전", "accuracy", true, 0, 20, null);

        verify(contestQueryRepository, org.mockito.Mockito.times(2))
                .searchContests(
                        captor.capture(), eq(ContestSearchSort.ACCURACY), any(), eq(0L), eq(20));
        assertThat(captor.getAllValues())
                .extracting(ContestSearchCondition::status)
                .containsExactly(ContestStatus.ACTIVE, ContestStatus.ALL);
        assertThat(captor.getValue().keyword()).isEqualTo("공모전");
    }

    private Contest contestWithId(long id) {
        Contest contest = contestWithDeadline(LocalDate.now().plusDays(30));
        given(contest.getId()).willReturn(id);
        return contest;
    }

    @Test
    @DisplayName("로그인 회원이면 그 페이지 카드 중 북마크한 공모전만 isBookmarked=true")
    void 로그인_회원이면_북마크한_공모전만_표시한다() {
        List<Contest> contests = List.of(contestWithId(1L), contestWithId(2L), contestWithId(3L));
        given(contestQueryRepository.findContests(any(), any(), anyLong(), anyInt()))
                .willReturn(contests);
        given(bookmarkService.findBookmarkedContestIds(7L, List.of(1L, 2L, 3L)))
                .willReturn(Set.of(2L));

        PageResponse<ContestCardResponse> result =
                contestQueryService.getContests("active", "latest", null, null, 0, 20, 7L);

        assertThat(result.items())
                .extracting(ContestCardResponse::contestId, ContestCardResponse::isBookmarked)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1L, false),
                        org.assertj.core.groups.Tuple.tuple(2L, true),
                        org.assertj.core.groups.Tuple.tuple(3L, false));
    }

    @Test
    @DisplayName("비로그인이면 북마크를 조회하지 않고 모두 isBookmarked=false")
    void 비로그인이면_북마크를_조회하지_않는다() {
        List<Contest> contests = List.of(contestWithId(1L));
        given(contestQueryRepository.findContests(any(), any(), anyLong(), anyInt()))
                .willReturn(contests);

        PageResponse<ContestCardResponse> result =
                contestQueryService.getContests("active", "latest", null, null, 0, 20, null);

        assertThat(result.items())
                .extracting(ContestCardResponse::isBookmarked)
                .containsOnly(false);
        verify(bookmarkService, never()).findBookmarkedContestIds(any(), any());
    }

    @Test
    @DisplayName("통합 검색 공모전 카드도 로그인 회원의 북마크를 표시한다")
    void 검색_결과에도_북마크를_표시한다() {
        List<Contest> contests = List.of(contestWithId(5L));
        given(contestQueryRepository.searchContests(any(), any(), any(), anyLong(), anyInt()))
                .willReturn(contests);
        given(bookmarkService.findBookmarkedContestIds(7L, List.of(5L))).willReturn(Set.of(5L));

        PageResponse<ContestCardResponse> result =
                contestQueryService.searchContests("공모전", "accuracy", false, 0, 20, 7L);

        assertThat(result.items().get(0).isBookmarked()).isTrue();
    }
}
