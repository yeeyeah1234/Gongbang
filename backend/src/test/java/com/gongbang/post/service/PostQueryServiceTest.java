package com.gongbang.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.gongbang.comment.service.CommentCountService;
import com.gongbang.contest.dto.ContestSummary;
import com.gongbang.contest.service.ContestLookupService;
import com.gongbang.global.common.PageResponse;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.dto.MemberProfileSummary;
import com.gongbang.member.service.MemberService;
import com.gongbang.post.domain.ExternalContest;
import com.gongbang.post.domain.PostSearchSort;
import com.gongbang.post.domain.PostStatus;
import com.gongbang.post.domain.RecruitPost;
import com.gongbang.post.dto.MyPostItemResponse;
import com.gongbang.post.dto.PostListItemResponse;
import com.gongbang.post.repository.RecruitPostRepository;
import com.gongbang.post.repository.RecruitPostSearchRepository;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PostQueryServiceTest {

    private static final LocalDate DEADLINE = LocalDate.of(2026, 12, 31);

    @Mock private RecruitPostRepository recruitPostRepository;
    @Mock private RecruitPostSearchRepository recruitPostSearchRepository;
    @Mock private ContestLookupService contestLookupService;
    @Mock private MemberService memberService;
    @Mock private CommentCountService commentCountService;

    private PostQueryService postQueryService;

    @BeforeEach
    void setUp() {
        postQueryService =
                new PostQueryService(
                        recruitPostRepository,
                        contestLookupService,
                        memberService,
                        commentCountService,
                        recruitPostSearchRepository);
        // 닉네임을 보지 않는 테스트용 기본값
        lenient()
                .when(memberService.getProfileSummaries(anyCollection()))
                .thenAnswer(
                        invocation -> {
                            Collection<Long> ids = invocation.getArgument(0);
                            return ids.stream()
                                    .collect(
                                            Collectors.toMap(
                                                    Function.identity(), id -> summary(id, "닉네임")));
                        });
    }

    private static MemberProfileSummary summary(Long memberId, String nickname) {
        return new MemberProfileSummary(memberId, nickname, null, List.of());
    }

    private static RecruitPost contestPost(Long postId, Long contestId) {
        RecruitPost post =
                RecruitPost.withContest(
                        7L, contestId, "AI 팀원 모집", "내용", 4, DEADLINE, DEADLINE, null);
        ReflectionTestUtils.setField(post, "id", postId);
        return post;
    }

    private static RecruitPost externalPost(Long postId, Long categoryId) {
        RecruitPost post =
                RecruitPost.withExternalContest(
                        8L,
                        ExternalContest.of("외부 해커톤", categoryId, DEADLINE, null),
                        "해커톤 팀원",
                        "내용",
                        3,
                        DEADLINE,
                        DEADLINE,
                        null);
        ReflectionTestUtils.setField(post, "id", postId);
        return post;
    }

    private void givenPage(List<RecruitPost> posts, long total) {
        Page<RecruitPost> page = new PageImpl<>(posts, PageRequest.of(0, 20), total);
        given(recruitPostRepository.findByDeletedAtIsNull(any(Pageable.class))).willReturn(page);
    }

    @Test
    @DisplayName("서비스 공모전 글은 공모전 제목·분야·마감일을, 외부 공모전 글은 「외부」 표시와 입력한 정보를 담는다")
    void 서비스_공모전과_외부_공모전_글을_함께_보여_준다() {
        givenPage(List.of(contestPost(2L, 3L), externalPost(1L, 5L)), 2);
        given(contestLookupService.findSummaries(Set.of(3L)))
                .willReturn(
                        Map.of(3L, new ContestSummary(3L, "AI 챌린지", "IT/소프트웨어", DEADLINE, null)));
        given(contestLookupService.findCategoryNames(Set.of(5L))).willReturn(Map.of(5L, "기타"));

        PageResponse<PostListItemResponse> result = postQueryService.getPosts(0, 20);

        assertThat(result.totalCount()).isEqualTo(2);
        PostListItemResponse service = result.items().get(0);
        assertThat(service.postId()).isEqualTo(2L);
        assertThat(service.contest().contestId()).isEqualTo(3L);
        assertThat(service.contest().title()).isEqualTo("AI 챌린지");
        assertThat(service.contest().categoryName()).isEqualTo("IT/소프트웨어");
        assertThat(service.contest().isExternal()).isFalse();
        assertThat(service.currentCount()).isEqualTo(1);
        assertThat(service.targetCount()).isEqualTo(4);
        assertThat(service.status()).isEqualTo(PostStatus.RECRUITING);
        // ④ 「모집마감」 표시용 모집 종료일
        assertThat(service.recruitEndDate()).isEqualTo(DEADLINE);
        assertThat(service.author().memberId()).isEqualTo(7L);

        PostListItemResponse external = result.items().get(1);
        assertThat(external.contest().contestId()).isNull();
        assertThat(external.contest().title()).isEqualTo("외부 해커톤");
        assertThat(external.contest().categoryName()).isEqualTo("기타");
        assertThat(external.contest().isExternal()).isTrue();
        assertThat(external.contest().deadline()).isEqualTo(DEADLINE);
    }

    @Test
    @DisplayName("작성자 닉네임을 한 번에 읽어 채운다 (②, 탈퇴 회원은 「탈퇴한 사용자」)")
    void 작성자_닉네임을_채운다() {
        givenPage(List.of(contestPost(3L, 3L), contestPost(2L, 3L), externalPost(1L, 5L)), 3);
        given(memberService.getProfileSummaries(Set.of(7L, 8L)))
                .willReturn(Map.of(7L, summary(7L, "김팀장"), 8L, summary(8L, "탈퇴한 사용자")));

        PageResponse<PostListItemResponse> result = postQueryService.getPosts(0, 20);

        assertThat(result.items())
                .extracting(item -> item.author().nickname())
                .containsExactly("김팀장", "김팀장", "탈퇴한 사용자");
        // 같은 작성자가 여러 글을 써도 회원 조회는 한 번
        verify(memberService, times(1)).getProfileSummaries(Set.of(7L, 8L));
        verify(memberService, never()).displayName(anyLong());
    }

    @Test
    @DisplayName("최신 등록순(같은 시각이면 ID 큰 순)으로 요청한 페이지를 읽는다")
    void 최신_등록순으로_요청한_페이지를_읽는다() {
        givenPage(List.of(), 0);
        given(contestLookupService.findSummaries(Set.of())).willReturn(Map.of());
        given(contestLookupService.findCategoryNames(Set.of())).willReturn(Map.of());

        postQueryService.getPosts(2, 20);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(recruitPostRepository).findByDeletedAtIsNull(captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(captor.getValue().getPageSize()).isEqualTo(20);
        assertThat(captor.getValue().getSort()).isEqualTo(PostQueryService.LATEST);
    }

    @Test
    @DisplayName("글이 없으면 빈 목록과 totalCount 0을 돌려준다")
    void 글이_없으면_빈_목록() {
        givenPage(List.of(), 0);
        given(contestLookupService.findSummaries(Set.of())).willReturn(Map.of());
        given(contestLookupService.findCategoryNames(Set.of())).willReturn(Map.of());

        PageResponse<PostListItemResponse> result = postQueryService.getPosts(0, 20);

        assertThat(result.items()).isEmpty();
        assertThat(result.totalCount()).isZero();
        assertThat(result.hasNext()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"-1, 20", "0, 0", "0, 51"})
    @DisplayName("page가 음수이거나 size가 1~50 밖이면 400 INVALID_INPUT")
    void 페이지_값이_틀리면_INVALID_INPUT(int page, int size) {
        BusinessException e =
                assertThrows(BusinessException.class, () -> postQueryService.getPosts(page, size));

        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
        verify(recruitPostRepository, never()).findByDeletedAtIsNull(any());
    }

    @Test
    @DisplayName("내 모집글은 내가 쓴 삭제 안 된 글을 최신순 페이지로 주고, 글마다 댓글 수를 채운다 (댓글 없으면 0, MY-02)")
    void 내_모집글을_최신순_페이지로_준다() {
        RecruitPost post = contestPost(1L, 3L);
        ReflectionTestUtils.setField(post, "status", PostStatus.COMPLETED);
        RecruitPost noComment = contestPost(2L, 3L);
        given(recruitPostRepository.findByAuthorIdAndDeletedAtIsNull(any(), any()))
                .willReturn(new PageImpl<>(List.of(post, noComment), PageRequest.of(1, 10), 12));
        given(commentCountService.countByPostIds(List.of(1L, 2L))).willReturn(Map.of(1L, 4L));

        PageResponse<MyPostItemResponse> response = postQueryService.getMyPosts(7L, 1, 10);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(recruitPostRepository).findByAuthorIdAndDeletedAtIsNull(eq(7L), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(10);
        assertThat(pageable.getValue().getSort()).isEqualTo(PostQueryService.LATEST);

        assertThat(response.totalCount()).isEqualTo(12);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.items())
                .containsExactly(
                        new MyPostItemResponse(
                                1L, "AI 팀원 모집", post.getCreatedAt(), 4, PostStatus.COMPLETED),
                        new MyPostItemResponse(
                                2L,
                                "AI 팀원 모집",
                                noComment.getCreatedAt(),
                                0,
                                noComment.getStatus()));
    }

    @ParameterizedTest
    @CsvSource({"-1, 10", "0, 0", "0, 51"})
    @DisplayName("내 모집글 페이지 값이 범위를 벗어나면 400 INVALID_INPUT")
    void 내_모집글_페이지_값이_잘못되면_400(int page, int size) {
        BusinessException e =
                assertThrows(
                        BusinessException.class, () -> postQueryService.getMyPosts(7L, page, size));
        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
        verify(recruitPostRepository, never()).findByAuthorIdAndDeletedAtIsNull(any(), any());
    }

    @Test
    @DisplayName("검색은 앞뒤 공백을 지운 검색어·작성자·정렬·오늘 날짜·시작 위치를 넘기고, PST-001 목록 항목으로 돌려준다 (SRCH-05 ①)")
    void 검색_결과를_목록_항목으로_준다() {
        RecruitPost post = contestPost(1L, 3L);
        given(
                        recruitPostSearchRepository.search(
                                eq("AI 팀원"),
                                eq(Set.of(7L)),
                                eq(PostSearchSort.DEADLINE),
                                any(LocalDate.class),
                                eq(20L),
                                eq(20)))
                .willReturn(List.of(post));
        given(recruitPostSearchRepository.count("AI 팀원", Set.of(7L))).willReturn(21L);
        given(contestLookupService.findSummaries(Set.of(3L)))
                .willReturn(Map.of(3L, new ContestSummary(3L, "AI 공모전", "IT", DEADLINE, null)));
        given(contestLookupService.findCategoryNames(Set.of())).willReturn(Map.of());

        PageResponse<PostListItemResponse> result =
                postQueryService.searchPosts(" AI 팀원 ", Set.of(7L), "deadline", 1, 20);

        assertThat(result.items()).extracting(PostListItemResponse::postId).containsExactly(1L);
        assertThat(result.items().get(0).contest().title()).isEqualTo("AI 공모전");
        assertThat(result.totalCount()).isEqualTo(21);
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    @DisplayName("정렬을 비우면 정확도순, 모르는 정렬·빈 검색어·잘못된 페이지는 400 INVALID_INPUT (SRCH-02 ②)")
    void 검색_입력_검사() {
        assertThat(PostSearchSort.from(null)).isEqualTo(PostSearchSort.ACCURACY);
        assertThat(PostSearchSort.from("latest")).isEqualTo(PostSearchSort.LATEST);
        for (Runnable bad :
                List.<Runnable>of(
                        () -> postQueryService.searchPosts("AI", null, "views", 0, 20),
                        () -> postQueryService.searchPosts("  ", null, null, 0, 20),
                        () -> postQueryService.searchPosts(null, null, null, 0, 20),
                        () -> postQueryService.searchPosts("AI", null, null, -1, 20),
                        () -> postQueryService.searchPosts("AI", null, null, 0, 51))) {
            assertThat(assertThrows(BusinessException.class, bad::run).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_INPUT);
        }
        verify(recruitPostSearchRepository, never())
                .search(any(), any(), any(), any(), anyLong(), any(Integer.class));
    }
}
