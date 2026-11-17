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
import com.gongbang.post.domain.PostStatus;
import com.gongbang.post.domain.RecruitPost;
import com.gongbang.post.repository.RecruitPostRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** PST-004 모집글 삭제 (PostCommandService.delete). */
@ExtendWith(MockitoExtension.class)
class PostDeleteServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 13, 15, 0);
    private static final Long POST_ID = 10L;
    private static final Long AUTHOR_ID = 7L;

    @Mock private RecruitPostRepository recruitPostRepository;
    @Mock private ContestDetailService contestDetailService;
    @Mock private ContestQueryService contestQueryService;
    @Mock private PostDetailService postDetailService;
    @Mock private ApplicationCloseService applicationCloseService;

    private PostCommandService postCommandService;

    @BeforeEach
    void setUp() {
        postCommandService =
                new PostCommandService(
                        recruitPostRepository,
                        contestDetailService,
                        contestQueryService,
                        postDetailService,
                        applicationCloseService);
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
                        () -> postCommandService.delete(memberId, POST_ID, NOW))
                .getErrorCode();
    }

    @Test
    @DisplayName("작성자가 지우면 삭제 시각을 남긴다 (소프트 삭제)")
    void 작성자가_지우면_소프트_삭제된다() {
        RecruitPost post = givenPost();

        postCommandService.delete(AUTHOR_ID, POST_ID, NOW);

        assertThat(post.getDeletedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("지우면 같은 시각으로 이 글의 대기 지원을 모두 종료한다 (④)")
    void 지우면_대기_지원을_종료한다() {
        givenPost();

        postCommandService.delete(AUTHOR_ID, POST_ID, NOW);

        verify(applicationCloseService).closePendingApplications(POST_ID, NOW);
    }

    @Test
    @DisplayName("확정 팀원이 있는 모집완료 글도 소프트 삭제하고 인원·상태는 그대로 둔다")
    void 팀원이_있는_글도_소프트_삭제된다() {
        RecruitPost post = givenPost();
        ReflectionTestUtils.setField(post, "currentCount", 4);
        ReflectionTestUtils.setField(post, "status", PostStatus.COMPLETED);

        postCommandService.delete(AUTHOR_ID, POST_ID, NOW);

        assertThat(post.getDeletedAt()).isEqualTo(NOW);
        assertThat(post.getCurrentCount()).isEqualTo(4);
        assertThat(post.getStatus()).isEqualTo(PostStatus.COMPLETED);
    }

    @Test
    @DisplayName("작성자가 아니면 403 NOT_POST_AUTHOR이고 지우지 않는다")
    void 작성자가_아니면_403() {
        RecruitPost post = givenPost();

        assertThat(errorOf(99L)).isEqualTo(ErrorCode.NOT_POST_AUTHOR);
        assertThat(post.getDeletedAt()).isNull();
        verifyNoInteractions(applicationCloseService);
    }

    @Test
    @DisplayName("없는 글이면 404 POST_NOT_FOUND")
    void 없는_글이면_404() {
        given(recruitPostRepository.findByIdForUpdate(POST_ID)).willReturn(Optional.empty());

        assertThat(errorOf(AUTHOR_ID)).isEqualTo(ErrorCode.POST_NOT_FOUND);
    }

    @Test
    @DisplayName("이미 삭제된 글을 다시 지우면 404 POST_NOT_FOUND이고 삭제 시각은 바뀌지 않는다")
    void 이미_삭제된_글이면_404() {
        RecruitPost post = givenPost();
        LocalDateTime deletedAt = NOW.minusDays(1);
        ReflectionTestUtils.setField(post, "deletedAt", deletedAt);

        assertThat(errorOf(AUTHOR_ID)).isEqualTo(ErrorCode.POST_NOT_FOUND);
        assertThat(post.getDeletedAt()).isEqualTo(deletedAt);
    }
}
