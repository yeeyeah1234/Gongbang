package com.gongbang.board.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.gongbang.board.domain.BoardCategory;
import com.gongbang.board.domain.BoardPost;
import com.gongbang.board.domain.BoardPostSearchSort;
import com.gongbang.board.dto.BoardPostDetailResponse;
import com.gongbang.board.repository.BoardPostRepository;
import com.gongbang.board.repository.BoardPostSearchRepository;
import com.gongbang.comment.service.CommentCountService;
import com.gongbang.contest.dto.ContestSummary;
import com.gongbang.contest.service.ContestLookupService;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.dto.MemberProfileSummary;
import com.gongbang.member.service.MemberService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

/** BRD-001 목록 정렬·검사, BRD-004 조회수·isLiked·isAuthor, BRD-008 인기글 기간. */
@ExtendWith(MockitoExtension.class)
class BoardPostQueryServiceTest {

    private static final Long AUTHOR_ID = 1L;
    private static final Long VIEWER_ID = 2L;
    private static final Long POST_ID = 10L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 23, 12, 0);

    @Mock private BoardPostRepository boardPostRepository;
    @Mock private ContestLookupService contestLookupService;
    @Mock private MemberService memberService;
    @Mock private BoardPostImageService boardPostImageService;
    @Mock private CommentCountService commentCountService;
    @Mock private BoardPostSearchRepository boardPostSearchRepository;

    private BoardPostQueryService service;

    @BeforeEach
    void setUp() {
        service =
                new BoardPostQueryService(
                        boardPostRepository,
                        contestLookupService,
                        memberService,
                        boardPostImageService,
                        commentCountService,
                        boardPostSearchRepository);
    }

    private static BoardPost post(Long contestId) {
        BoardPost post =
                BoardPost.of(AUTHOR_ID, BoardCategory.CONTEST, contestId, "제목", "내용", null);
        ReflectionTestUtils.setField(post, "id", POST_ID);
        ReflectionTestUtils.setField(post, "viewCount", 5);
        return post;
    }

    private void givenPost(BoardPost post) {
        given(boardPostRepository.findById(POST_ID)).willReturn(Optional.of(post));
        given(memberService.getProfileSummary(AUTHOR_ID))
                .willReturn(new MemberProfileSummary(AUTHOR_ID, "작성자", "/p.png", List.of()));
    }

    @Test
    @DisplayName("목록: 정렬은 latest(기본)·popular만, 페이지 범위가 틀리면 400")
    void 목록_검사() {
        given(boardPostRepository.findActive(any(), any(), any()))
                .willReturn(new PageImpl<>(List.of()));

        service.getPosts(null, null, null, 0, 20);
        service.getPosts(null, null, "popular", 0, 20);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(boardPostRepository, org.mockito.Mockito.times(2))
                .findActive(any(), any(), pageable.capture());
        assertThat(pageable.getAllValues().get(0).getSort())
                .isEqualTo(BoardPostQueryService.LATEST);
        assertThat(pageable.getAllValues().get(1).getSort())
                .isEqualTo(BoardPostQueryService.POPULAR);

        for (Runnable bad :
                List.<Runnable>of(
                        () -> service.getPosts(null, null, "views", 0, 20),
                        () -> service.getPosts(null, null, null, -1, 20),
                        () -> service.getPosts(null, null, null, 0, 51))) {
            assertThat(catchThrowableOfType(bad::run, BusinessException.class).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_INPUT);
        }
    }

    @Test
    @DisplayName("목록 항목에 공모전 이름, 작성자 닉네임, 댓글 수를 붙인다 (BRD-001 ④)")
    void 목록_항목() {
        given(boardPostRepository.findActive(any(), any(), any()))
                .willReturn(new PageImpl<>(List.of(post(3L))));
        given(contestLookupService.findSummaries(any()))
                .willReturn(
                        Map.of(
                                3L,
                                new ContestSummary(
                                        3L, "공모전", "IT", LocalDate.of(2026, 12, 1), null)));
        given(memberService.getProfileSummaries(any()))
                .willReturn(
                        Map.of(
                                AUTHOR_ID,
                                new MemberProfileSummary(AUTHOR_ID, "작성자", null, List.of())));

        given(commentCountService.countByBoardPostIds(List.of(POST_ID)))
                .willReturn(Map.of(POST_ID, 2L));

        var item = service.getPosts(null, null, null, 0, 20).items().get(0);

        assertThat(item.contest().title()).isEqualTo("공모전");
        assertThat(item.author().nickname()).isEqualTo("작성자");
        assertThat(item.viewCount()).isEqualTo(5);
        assertThat(item.commentCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("인기글: 지금부터 7일 전 이후에 쓴 글을 3개까지 찾는다 (BRD-008 ②)")
    void 인기글_기간() {
        given(boardPostRepository.findPopular(any(), any(), any())).willReturn(List.of());

        service.getPopular(BoardCategory.TIP, NOW);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(boardPostRepository)
                .findPopular(eq(BoardCategory.TIP), eq(NOW.minusDays(7)), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(3);
    }

    @Test
    @DisplayName("상세: 회원이 처음 열면 조회수 +1, isLiked·isAuthor를 준다 (BRD-004 ⑤)")
    void 상세_회원() {
        givenPost(post(null));
        given(boardPostRepository.touchStaleView(eq(POST_ID), eq(VIEWER_ID), eq(NOW), any()))
                .willReturn(0);
        given(boardPostRepository.insertViewIfAbsent(POST_ID, VIEWER_ID, NOW)).willReturn(1);
        given(boardPostRepository.countLike(POST_ID, VIEWER_ID)).willReturn(1L);

        BoardPostDetailResponse response = service.getPost(POST_ID, VIEWER_ID, NOW);

        verify(boardPostRepository).increaseViewCount(POST_ID);
        assertThat(response.viewCount()).isEqualTo(6);
        assertThat(response.isLiked()).isTrue();
        assertThat(response.isAuthor()).isFalse();
        assertThat(response.contest()).isNull();
        assertThat(response.author().nickname()).isEqualTo("작성자");
    }

    @Test
    @DisplayName("상세: 24시간 안에 다시 열면 조회수가 오르지 않는다")
    void 상세_재조회() {
        givenPost(post(null));
        given(boardPostRepository.touchStaleView(eq(POST_ID), eq(VIEWER_ID), eq(NOW), any()))
                .willReturn(0);
        given(boardPostRepository.insertViewIfAbsent(POST_ID, VIEWER_ID, NOW)).willReturn(0);

        assertThat(service.getPost(POST_ID, VIEWER_ID, NOW).viewCount()).isEqualTo(5);
        verify(boardPostRepository, never()).increaseViewCount(anyLong());
    }

    @Test
    @DisplayName("상세: 비회원은 조회수를 올리지 않고 isLiked·isAuthor는 false, 작성자는 isAuthor true")
    void 상세_비회원과_작성자() {
        givenPost(post(null));

        BoardPostDetailResponse guest = service.getPost(POST_ID, null, NOW);
        assertThat(guest.isLiked()).isFalse();
        assertThat(guest.isAuthor()).isFalse();
        verify(boardPostRepository, never()).increaseViewCount(anyLong());

        given(boardPostRepository.touchStaleView(eq(POST_ID), eq(AUTHOR_ID), eq(NOW), any()))
                .willReturn(1);
        assertThat(service.getPost(POST_ID, AUTHOR_ID, NOW).isAuthor()).isTrue();
    }

    @Test
    @DisplayName("상세: 공모전 카드는 D-day를 계산한다, 없거나 삭제된 글은 404")
    void 상세_공모전과_404() {
        givenPost(post(3L));
        given(contestLookupService.findSummary(3L))
                .willReturn(
                        Optional.of(
                                new ContestSummary(
                                        3L, "공모전", "IT", NOW.toLocalDate().plusDays(4), null)));
        assertThat(service.getPost(POST_ID, null, NOW).contest().dDay()).isEqualTo(4);

        given(boardPostRepository.findById(99L)).willReturn(Optional.empty());
        assertThat(
                        catchThrowableOfType(
                                        () -> service.getPost(99L, null, NOW),
                                        BusinessException.class)
                                .getErrorCode())
                .isEqualTo(ErrorCode.BOARD_POST_NOT_FOUND);
    }

    @Test
    @DisplayName("검색은 앞뒤 공백을 지운 검색어·작성자·정렬·시작 위치를 넘기고, BRD-001 목록 항목으로 돌려준다 (SRCH-05 ①)")
    void 검색_결과를_목록_항목으로_준다() {
        given(
                        boardPostSearchRepository.search(
                                eq("후기"),
                                eq(Set.of(AUTHOR_ID)),
                                eq(BoardPostSearchSort.DEADLINE),
                                eq(20L),
                                eq(20)))
                .willReturn(List.of(post(3L)));
        given(boardPostSearchRepository.count("후기", Set.of(AUTHOR_ID))).willReturn(21L);
        given(contestLookupService.findSummaries(any()))
                .willReturn(
                        Map.of(
                                3L,
                                new ContestSummary(
                                        3L, "공모전", "IT", LocalDate.of(2026, 12, 1), null)));
        given(memberService.getProfileSummaries(any()))
                .willReturn(
                        Map.of(
                                AUTHOR_ID,
                                new MemberProfileSummary(AUTHOR_ID, "작성자", null, List.of())));

        var result = service.searchPosts(" 후기 ", Set.of(AUTHOR_ID), "deadline", 1, 20);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).contest().title()).isEqualTo("공모전");
        assertThat(result.items().get(0).author().nickname()).isEqualTo("작성자");
        assertThat(result.totalCount()).isEqualTo(21);
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    @DisplayName("정렬을 비우면 정확도순, 모르는 정렬·빈 검색어·잘못된 페이지는 400 INVALID_INPUT (SRCH-02 ②)")
    void 검색_입력_검사() {
        assertThat(BoardPostSearchSort.from(null)).isEqualTo(BoardPostSearchSort.ACCURACY);
        assertThat(BoardPostSearchSort.from("deadline")).isEqualTo(BoardPostSearchSort.DEADLINE);
        for (Runnable bad :
                List.<Runnable>of(
                        () -> service.searchPosts("후기", null, "popular", 0, 20),
                        () -> service.searchPosts("  ", null, null, 0, 20),
                        () -> service.searchPosts(null, null, null, 0, 20),
                        () -> service.searchPosts("후기", null, null, -1, 20),
                        () -> service.searchPosts("후기", null, null, 0, 51))) {
            BusinessException e = catchThrowableOfType(bad::run, BusinessException.class);
            assertThat(e).isNotNull();
            assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
        }
        verify(boardPostSearchRepository, never())
                .search(any(), any(), any(), anyLong(), any(Integer.class));
    }
}
