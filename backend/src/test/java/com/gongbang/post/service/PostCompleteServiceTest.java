package com.gongbang.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.gongbang.application.service.ApplicationCloseService;
import com.gongbang.contest.service.ContestDetailService;
import com.gongbang.contest.service.ContestQueryService;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.invitation.service.InvitationCancelService;
import com.gongbang.post.domain.PostStatus;
import com.gongbang.post.domain.RecruitPost;
import com.gongbang.post.dto.PostCompleteResponse;
import com.gongbang.post.repository.RecruitPostRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

/** REC-006 수동 모집 완료 (PostCommandService.complete). */
@ExtendWith(MockitoExtension.class)
class PostCompleteServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 17, 15, 0);
    private static final Long POST_ID = 10L;
    private static final Long AUTHOR_ID = 7L;

    @Mock private RecruitPostRepository recruitPostRepository;
    @Mock private ContestDetailService contestDetailService;
    @Mock private ContestQueryService contestQueryService;
    @Mock private PostDetailService postDetailService;
    @Mock private ApplicationCloseService applicationCloseService;
    @Mock private InvitationCancelService invitationCancelService;
    @Mock private RecruitNotifier recruitNotifier;
    @Mock private ApplicationEventPublisher eventPublisher;

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
                        invitationCancelService,
                        recruitNotifier,
                        eventPublisher);
    }

    private RecruitPost givenPost() {
        LocalDate start = NOW.toLocalDate();
        RecruitPost post =
                RecruitPost.withContest(
                        AUTHOR_ID, 3L, "제목", "내용", 4, start, start.plusDays(7), null);
        ReflectionTestUtils.setField(post, "id", POST_ID);
        given(recruitPostRepository.findByIdForUpdate(POST_ID)).willReturn(Optional.of(post));
        return post;
    }

    private ErrorCode errorOf(Long memberId) {
        return assertThrows(
                        BusinessException.class,
                        () -> postCommandService.complete(memberId, POST_ID, NOW))
                .getErrorCode();
    }

    @Test
    @DisplayName("정원이 안 찬 글도 작성자가 누르면 모집완료가 되고 완료 시각을 남기며, 대기 초대를 취소한다 (②, INT-005 ④)")
    void 정원이_안_차도_모집완료된다() {
        RecruitPost post = givenPost();

        postCommandService.complete(AUTHOR_ID, POST_ID, NOW);

        assertThat(post.getStatus()).isEqualTo(PostStatus.COMPLETED);
        assertThat(post.getCompletedAt()).isEqualTo(NOW);
        verify(invitationCancelService).cancelPendingByPostId(POST_ID, NOW);
    }

    @Test
    @DisplayName("남은 대기 지원을 자동 거절하고, 지원자마다 보낸 사람 없이 거절 알림을 보낸다 (④)")
    void 대기_지원을_자동_거절하고_알린다() {
        RecruitPost post = givenPost();
        given(applicationCloseService.autoRejectPendingApplications(POST_ID, NOW))
                .willReturn(List.of(21L, 22L));

        PostCompleteResponse response = postCommandService.complete(AUTHOR_ID, POST_ID, NOW);

        assertThat(response.autoRejectedCount()).isEqualTo(2);
        verify(recruitNotifier).rejected(post, 21L, null);
        verify(recruitNotifier).rejected(post, 22L, null);
    }

    @Test
    @DisplayName("대기 지원이 없으면 autoRejectedCount 0, 알림 없음")
    void 대기_지원이_없으면_0() {
        givenPost();
        given(applicationCloseService.autoRejectPendingApplications(POST_ID, NOW))
                .willReturn(List.of());

        PostCompleteResponse response = postCommandService.complete(AUTHOR_ID, POST_ID, NOW);

        assertThat(response.autoRejectedCount()).isZero();
        verifyNoInteractions(recruitNotifier);
    }

    @Test
    @DisplayName("작성자가 아니면 403 NOT_POST_AUTHOR, 글과 지원은 그대로")
    void 작성자가_아니면_403() {
        RecruitPost post = givenPost();

        assertThat(errorOf(99L)).isEqualTo(ErrorCode.NOT_POST_AUTHOR);
        assertThat(post.getStatus()).isEqualTo(PostStatus.RECRUITING);
        verifyNoInteractions(applicationCloseService, recruitNotifier);
    }

    @Test
    @DisplayName("이미 모집완료된 글이면 409 POST_COMPLETED (REC-007 재개 없음)")
    void 이미_모집완료면_409() {
        RecruitPost post = givenPost();
        LocalDateTime before = NOW.minusDays(1);
        post.complete(before);

        assertThat(errorOf(AUTHOR_ID)).isEqualTo(ErrorCode.POST_COMPLETED);
        assertThat(post.getCompletedAt()).isEqualTo(before);
        verifyNoInteractions(applicationCloseService, recruitNotifier);
    }

    @Test
    @DisplayName("없는 글이면 404 POST_NOT_FOUND")
    void 없는_글이면_404() {
        given(recruitPostRepository.findByIdForUpdate(POST_ID)).willReturn(Optional.empty());

        assertThat(errorOf(AUTHOR_ID)).isEqualTo(ErrorCode.POST_NOT_FOUND);
    }

    @Test
    @DisplayName("삭제된 글이면 404 POST_NOT_FOUND")
    void 삭제된_글이면_404() {
        RecruitPost post = givenPost();
        post.delete(NOW.minusHours(1));

        assertThat(errorOf(AUTHOR_ID)).isEqualTo(ErrorCode.POST_NOT_FOUND);
    }
}
