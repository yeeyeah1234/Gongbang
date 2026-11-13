package com.gongbang.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.gongbang.application.domain.ApplicationStatus;
import com.gongbang.application.dto.MyApplication;
import com.gongbang.application.service.ApplicationQueryService;
import com.gongbang.contest.dto.ContestSummary;
import com.gongbang.contest.service.ContestLookupService;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.post.domain.ExternalContest;
import com.gongbang.post.domain.MyStatus;
import com.gongbang.post.domain.RecruitPost;
import com.gongbang.post.dto.PostDetailResponse;
import com.gongbang.post.repository.RecruitPostRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PostDetailServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 13, 15, 0);
    private static final LocalDate DEADLINE = LocalDate.of(2026, 11, 23);
    private static final Long POST_ID = 10L;
    private static final Long AUTHOR_ID = 7L;
    private static final Long VIEWER_ID = 20L;

    @Mock private RecruitPostRepository recruitPostRepository;
    @Mock private ContestLookupService contestLookupService;
    @Mock private ApplicationQueryService applicationQueryService;

    private PostDetailService postDetailService;

    @BeforeEach
    void setUp() {
        postDetailService =
                new PostDetailService(
                        recruitPostRepository, contestLookupService, applicationQueryService);
    }

    private void givenViewedBefore(Long viewerId) {
        given(recruitPostRepository.touchStaleView(POST_ID, viewerId, NOW, NOW.minusHours(24)))
                .willReturn(0);
        given(recruitPostRepository.insertViewIfAbsent(POST_ID, viewerId, NOW)).willReturn(0);
    }

    @Test
    @DisplayName("대기 중인 지원이 있는 회원은 myStatus PENDING과 그 지원 ID를 받는다 (지원 취소용)")
    void 대기_지원자는_PENDING과_지원_ID() {
        givenContestPost(5);
        givenViewedBefore(VIEWER_ID);
        given(applicationQueryService.findMyApplication(POST_ID, VIEWER_ID))
                .willReturn(Optional.of(new MyApplication(55L, ApplicationStatus.PENDING)));

        PostDetailResponse response = postDetailService.getPost(POST_ID, VIEWER_ID, NOW);

        assertThat(response.myStatus()).isEqualTo(MyStatus.PENDING);
        assertThat(response.myApplicationId()).isEqualTo(55L);
    }

    @Test
    @DisplayName("거절된 회원은 myStatus REJECTED")
    void 거절된_지원자는_REJECTED() {
        givenContestPost(5);
        givenViewedBefore(VIEWER_ID);
        given(applicationQueryService.findMyApplication(POST_ID, VIEWER_ID))
                .willReturn(Optional.of(new MyApplication(56L, ApplicationStatus.REJECTED)));

        assertThat(postDetailService.getPost(POST_ID, VIEWER_ID, NOW).myStatus())
                .isEqualTo(MyStatus.REJECTED);
    }

    @Test
    @DisplayName("작성자와 비회원은 지원 기록을 찾지 않는다")
    void 작성자와_비회원은_지원을_찾지_않는다() {
        givenContestPost(5);
        givenViewedBefore(AUTHOR_ID);

        postDetailService.getPost(POST_ID, AUTHOR_ID, NOW);
        postDetailService.getPost(POST_ID, null, NOW);

        verify(applicationQueryService, never()).findMyApplication(any(), any());
    }

    private RecruitPost givenContestPost(int viewCount) {
        RecruitPost post =
                RecruitPost.withContest(
                        AUTHOR_ID,
                        3L,
                        "AI 팀원 모집",
                        "내용",
                        4,
                        NOW.toLocalDate(),
                        DEADLINE,
                        "https://example.com");
        ReflectionTestUtils.setField(post, "id", POST_ID);
        ReflectionTestUtils.setField(post, "viewCount", viewCount);
        given(recruitPostRepository.findById(POST_ID)).willReturn(Optional.of(post));
        given(contestLookupService.findSummary(3L))
                .willReturn(
                        Optional.of(
                                new ContestSummary(
                                        3L,
                                        "AI 챌린지",
                                        "IT/소프트웨어",
                                        DEADLINE,
                                        "https://img.example.com/a.png")));
        return post;
    }

    private BusinessException getPostFails(Long postId) {
        return assertThrows(
                BusinessException.class, () -> postDetailService.getPost(postId, null, NOW));
    }

    @Test
    @DisplayName("없는 글이면 404 POST_NOT_FOUND")
    void 없는_글이면_404() {
        given(recruitPostRepository.findById(99L)).willReturn(Optional.empty());

        assertThat(getPostFails(99L).getErrorCode()).isEqualTo(ErrorCode.POST_NOT_FOUND);
    }

    @Test
    @DisplayName("삭제된 글이면 404 POST_NOT_FOUND이고 조회수를 올리지 않는다")
    void 삭제된_글이면_404() {
        RecruitPost post =
                RecruitPost.withContest(
                        AUTHOR_ID, 3L, "삭제된 글", "내용", 4, NOW.toLocalDate(), DEADLINE, null);
        ReflectionTestUtils.setField(post, "deletedAt", NOW.minusDays(1));
        given(recruitPostRepository.findById(POST_ID)).willReturn(Optional.of(post));

        assertThat(getPostFails(POST_ID).getErrorCode()).isEqualTo(ErrorCode.POST_NOT_FOUND);
        verify(recruitPostRepository, never()).increaseViewCount(anyLong());
    }

    @Test
    @DisplayName("서비스 공모전 글은 공모전 썸네일을 대표 이미지로 쓰고 D-day를 계산한다")
    void 서비스_공모전_글_상세() {
        givenContestPost(5);

        PostDetailResponse response = postDetailService.getPost(POST_ID, null, NOW);

        assertThat(response.postId()).isEqualTo(POST_ID);
        assertThat(response.content()).isEqualTo("내용");
        assertThat(response.imageUrl()).isEqualTo("https://img.example.com/a.png");
        assertThat(response.contest().contestId()).isEqualTo(3L);
        assertThat(response.contest().isExternal()).isFalse();
        assertThat(response.contest().dDay()).isEqualTo(10);
        assertThat(response.contest().url()).isNull();
        assertThat(response.relatedUrl()).isEqualTo("https://example.com");
        assertThat(response.recruitEndDate()).isEqualTo(DEADLINE);
    }

    @Test
    @DisplayName("외부 공모전 글은 입력한 링크와 불러온 대표 이미지, 분야 이름을 보여 준다")
    void 외부_공모전_글_상세() {
        ExternalContest external =
                ExternalContest.of("외부 해커톤", 5L, DEADLINE, "https://hack.example.com");
        ReflectionTestUtils.setField(external, "imageUrl", "https://hack.example.com/og.png");
        RecruitPost post =
                RecruitPost.withExternalContest(
                        AUTHOR_ID, external, "해커톤", "내용", 3, NOW.toLocalDate(), DEADLINE, null);
        ReflectionTestUtils.setField(post, "id", POST_ID);
        given(recruitPostRepository.findById(POST_ID)).willReturn(Optional.of(post));
        given(contestLookupService.findCategoryNames(Set.of(5L))).willReturn(Map.of(5L, "기타"));

        PostDetailResponse response = postDetailService.getPost(POST_ID, null, NOW);

        assertThat(response.contest().isExternal()).isTrue();
        assertThat(response.contest().contestId()).isNull();
        assertThat(response.contest().categoryName()).isEqualTo("기타");
        assertThat(response.contest().url()).isEqualTo("https://hack.example.com");
        assertThat(response.imageUrl()).isEqualTo("https://hack.example.com/og.png");
    }

    @Test
    @DisplayName("비회원이 열면 myStatus NONE이고 조회수를 올리지 않는다")
    void 비회원은_조회수를_올리지_않는다() {
        givenContestPost(5);

        PostDetailResponse response = postDetailService.getPost(POST_ID, null, NOW);

        assertThat(response.myStatus()).isEqualTo(MyStatus.NONE);
        assertThat(response.myApplicationId()).isNull();
        assertThat(response.viewCount()).isEqualTo(5);
        verify(recruitPostRepository, never()).touchStaleView(any(), any(), any(), any());
        verify(recruitPostRepository, never()).insertViewIfAbsent(any(), any(), any());
        verify(recruitPostRepository, never()).increaseViewCount(anyLong());
    }

    @Test
    @DisplayName("작성자가 열면 myStatus AUTHOR")
    void 작성자는_AUTHOR() {
        givenContestPost(5);
        given(recruitPostRepository.touchStaleView(POST_ID, AUTHOR_ID, NOW, NOW.minusHours(24)))
                .willReturn(0);
        given(recruitPostRepository.insertViewIfAbsent(POST_ID, AUTHOR_ID, NOW)).willReturn(0);

        assertThat(postDetailService.getPost(POST_ID, AUTHOR_ID, NOW).myStatus())
                .isEqualTo(MyStatus.AUTHOR);
    }

    @Test
    @DisplayName("회원이 처음 열면 기록을 넣고 조회수를 1 올린다")
    void 회원_첫_조회는_조회수가_오른다() {
        givenContestPost(5);
        given(recruitPostRepository.touchStaleView(POST_ID, VIEWER_ID, NOW, NOW.minusHours(24)))
                .willReturn(0);
        given(recruitPostRepository.insertViewIfAbsent(POST_ID, VIEWER_ID, NOW)).willReturn(1);

        PostDetailResponse response = postDetailService.getPost(POST_ID, VIEWER_ID, NOW);

        verify(recruitPostRepository).increaseViewCount(POST_ID);
        assertThat(response.viewCount()).isEqualTo(6);
        assertThat(response.myStatus()).isEqualTo(MyStatus.NONE);
    }

    @Test
    @DisplayName("같은 회원이 24시간 안에 다시 열면 조회수가 오르지 않는다")
    void 회원_24시간_안_재조회는_오르지_않는다() {
        givenContestPost(5);
        given(recruitPostRepository.touchStaleView(POST_ID, VIEWER_ID, NOW, NOW.minusHours(24)))
                .willReturn(0);
        given(recruitPostRepository.insertViewIfAbsent(POST_ID, VIEWER_ID, NOW)).willReturn(0);

        PostDetailResponse response = postDetailService.getPost(POST_ID, VIEWER_ID, NOW);

        verify(recruitPostRepository, never()).increaseViewCount(anyLong());
        assertThat(response.viewCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("24시간이 지나 다시 열면 기록을 갱신하고 조회수를 1 올린다")
    void 회원_24시간_지난_재조회는_오른다() {
        givenContestPost(5);
        given(recruitPostRepository.touchStaleView(POST_ID, VIEWER_ID, NOW, NOW.minusHours(24)))
                .willReturn(1);

        PostDetailResponse response = postDetailService.getPost(POST_ID, VIEWER_ID, NOW);

        verify(recruitPostRepository).increaseViewCount(POST_ID);
        verify(recruitPostRepository, never()).insertViewIfAbsent(any(), any(), any());
        assertThat(response.viewCount()).isEqualTo(6);
    }
}
