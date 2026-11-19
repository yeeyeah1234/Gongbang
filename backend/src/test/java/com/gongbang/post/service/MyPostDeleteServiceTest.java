package com.gongbang.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.gongbang.application.service.ApplicationCloseService;
import com.gongbang.contest.service.ContestDetailService;
import com.gongbang.contest.service.ContestQueryService;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.post.domain.RecruitPost;
import com.gongbang.post.dto.MyPostDeleteResponse;
import com.gongbang.post.repository.RecruitPostRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** MY-02 ③ 내 모집글 선택 삭제 (PostCommandService.deleteMine). */
@ExtendWith(MockitoExtension.class)
class MyPostDeleteServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 19, 15, 0);
    private static final Long AUTHOR_ID = 7L;

    @Mock private RecruitPostRepository recruitPostRepository;
    @Mock private ContestDetailService contestDetailService;
    @Mock private ContestQueryService contestQueryService;
    @Mock private PostDetailService postDetailService;
    @Mock private ApplicationCloseService applicationCloseService;
    @Mock private RecruitNotifier recruitNotifier;

    private PostCommandService postCommandService;

    @BeforeEach
    void setUp() {
        postCommandService =
                new PostCommandService(
                        recruitPostRepository,
                        contestDetailService,
                        contestQueryService,
                        postDetailService,
                        applicationCloseService,
                        recruitNotifier);
    }

    private RecruitPost givenPost(Long postId, Long authorId) {
        LocalDate start = NOW.toLocalDate();
        RecruitPost post =
                RecruitPost.withContest(
                        authorId, 3L, "제목", "내용", 4, start, start.plusDays(7), null);
        ReflectionTestUtils.setField(post, "id", postId);
        given(recruitPostRepository.findByIdForUpdate(postId)).willReturn(Optional.of(post));
        return post;
    }

    private ErrorCode errorOf(List<Long> postIds) {
        return assertThrows(
                        BusinessException.class,
                        () -> postCommandService.deleteMine(AUTHOR_ID, postIds, NOW))
                .getErrorCode();
    }

    @Test
    @DisplayName("고른 내 글을 모두 소프트 삭제하고 지운 개수를 준다")
    void 고른_내_글을_모두_지운다() {
        RecruitPost first = givenPost(10L, AUTHOR_ID);
        RecruitPost second = givenPost(11L, AUTHOR_ID);

        MyPostDeleteResponse response =
                postCommandService.deleteMine(AUTHOR_ID, List.of(10L, 11L), NOW);

        assertThat(response.deletedCount()).isEqualTo(2);
        assertThat(first.getDeletedAt()).isEqualTo(NOW);
        assertThat(second.getDeletedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("글마다 PST-004처럼 대기 지원을 종료하고 대기 지원자에게 삭제 알림을 보낸다")
    void 글마다_대기_지원을_종료하고_알린다() {
        RecruitPost post = givenPost(10L, AUTHOR_ID);
        given(applicationCloseService.closePendingApplications(10L, NOW)).willReturn(List.of(20L));

        postCommandService.deleteMine(AUTHOR_ID, List.of(10L), NOW);

        verify(applicationCloseService).closePendingApplications(10L, NOW);
        verify(recruitNotifier).postDeleted(post, 20L, AUTHOR_ID);
    }

    @Test
    @DisplayName("같은 ID는 한 번만 지우고, 여러 글은 ID 순으로 잠근다")
    void 중복은_한_번만_지우고_ID_순으로_잠근다() {
        givenPost(10L, AUTHOR_ID);
        givenPost(11L, AUTHOR_ID);

        MyPostDeleteResponse response =
                postCommandService.deleteMine(AUTHOR_ID, List.of(11L, 10L, 11L), NOW);

        assertThat(response.deletedCount()).isEqualTo(2);
        InOrder order = inOrder(recruitPostRepository);
        order.verify(recruitPostRepository).findByIdForUpdate(10L);
        order.verify(recruitPostRepository).findByIdForUpdate(11L);
        verify(recruitPostRepository, times(1)).findByIdForUpdate(11L);
    }

    @Test
    @DisplayName("하나라도 내 글이 아니면 403 NOT_POST_AUTHOR (트랜잭션이라 앞에서 지운 글도 되돌아간다)")
    void 내_글이_아니면_403() {
        givenPost(10L, AUTHOR_ID);
        givenPost(11L, 99L);

        assertThat(errorOf(List.of(10L, 11L))).isEqualTo(ErrorCode.NOT_POST_AUTHOR);
    }

    @Test
    @DisplayName("없거나 이미 삭제된 글이 섞이면 PST-004와 같은 404 POST_NOT_FOUND")
    void 없는_글이면_404() {
        given(recruitPostRepository.findByIdForUpdate(10L)).willReturn(Optional.empty());

        assertThat(errorOf(List.of(10L))).isEqualTo(ErrorCode.POST_NOT_FOUND);
    }

    @Test
    @DisplayName("고른 글이 없거나 빈 ID가 섞이면 400 INVALID_INPUT이고 아무것도 잠그지 않는다")
    void 고른_글이_없으면_400() {
        assertThat(errorOf(null)).isEqualTo(ErrorCode.INVALID_INPUT);
        assertThat(errorOf(List.of())).isEqualTo(ErrorCode.INVALID_INPUT);
        assertThat(errorOf(Arrays.asList(10L, null))).isEqualTo(ErrorCode.INVALID_INPUT);
        verify(recruitPostRepository, never()).findByIdForUpdate(any());
    }
}
