package com.gongbang.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.gongbang.contest.dto.ContestSummary;
import com.gongbang.contest.service.ContestLookupService;
import com.gongbang.global.common.PageResponse;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.service.MemberService;
import com.gongbang.post.domain.ExternalContest;
import com.gongbang.post.domain.PostStatus;
import com.gongbang.post.domain.RecruitPost;
import com.gongbang.post.dto.MyPostItemResponse;
import com.gongbang.post.dto.PostListItemResponse;
import com.gongbang.post.repository.RecruitPostRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    @Mock private ContestLookupService contestLookupService;
    @Mock private MemberService memberService;

    private PostQueryService postQueryService;

    @BeforeEach
    void setUp() {
        postQueryService =
                new PostQueryService(recruitPostRepository, contestLookupService, memberService);
        // 닉네임을 보지 않는 테스트용 기본값
        lenient().when(memberService.displayName(anyLong())).thenReturn("닉네임");
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
    @DisplayName("작성자 닉네임을 채우고, 같은 작성자는 한 번만 읽는다 (②, 탈퇴 회원은 「탈퇴한 사용자」)")
    void 작성자_닉네임을_채운다() {
        givenPage(List.of(contestPost(3L, 3L), contestPost(2L, 3L), externalPost(1L, 5L)), 3);
        given(memberService.displayName(7L)).willReturn("김팀장");
        given(memberService.displayName(8L)).willReturn("탈퇴한 사용자");

        PageResponse<PostListItemResponse> result = postQueryService.getPosts(0, 20);

        assertThat(result.items())
                .extracting(item -> item.author().nickname())
                .containsExactly("김팀장", "김팀장", "탈퇴한 사용자");
        verify(memberService, times(1)).displayName(7L);
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
    @DisplayName("내 모집글은 내가 쓴 삭제 안 된 글을 최신순 페이지로 주고, 댓글 수는 아직 0이다 (MY-02)")
    void 내_모집글을_최신순_페이지로_준다() {
        RecruitPost post = contestPost(1L, 3L);
        ReflectionTestUtils.setField(post, "status", PostStatus.COMPLETED);
        given(recruitPostRepository.findByAuthorIdAndDeletedAtIsNull(any(), any()))
                .willReturn(new PageImpl<>(List.of(post), PageRequest.of(1, 10), 11));

        PageResponse<MyPostItemResponse> response = postQueryService.getMyPosts(7L, 1, 10);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(recruitPostRepository).findByAuthorIdAndDeletedAtIsNull(eq(7L), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(10);
        assertThat(pageable.getValue().getSort()).isEqualTo(PostQueryService.LATEST);

        assertThat(response.totalCount()).isEqualTo(11);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.items())
                .containsExactly(
                        new MyPostItemResponse(
                                1L, "AI 팀원 모집", post.getCreatedAt(), 0, PostStatus.COMPLETED));
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
}
