package com.gongbang.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

import com.gongbang.application.service.ApplicationCloseService;
import com.gongbang.contest.service.ContestDetailService;
import com.gongbang.contest.service.ContestQueryService;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

/** 팀 나가기·강제 퇴장의 확정 인원 감소 (PostCommandService.decreaseCurrentCount, INT-006 ④⑤). */
@ExtendWith(MockitoExtension.class)
class PostDecreaseCountServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 17, 15, 0);
    private static final Long POST_ID = 10L;
    private static final Long AUTHOR_ID = 7L;

    @Mock private RecruitPostRepository recruitPostRepository;
    @Mock private ContestDetailService contestDetailService;
    @Mock private ContestQueryService contestQueryService;
    @Mock private PostDetailService postDetailService;
    @Mock private ApplicationCloseService applicationCloseService;
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
                        recruitNotifier,
                        eventPublisher);
    }

    /** 목표 4명, 확정 currentCount명인 글을 잠금 조회로 돌려준다. */
    private RecruitPost givenPost(int currentCount) {
        LocalDate start = NOW.toLocalDate();
        RecruitPost post =
                RecruitPost.withContest(
                        AUTHOR_ID, 3L, "제목", "내용", 4, start, start.plusDays(7), null);
        ReflectionTestUtils.setField(post, "id", POST_ID);
        ReflectionTestUtils.setField(post, "currentCount", currentCount);
        given(recruitPostRepository.findByIdForUpdate(POST_ID)).willReturn(Optional.of(post));
        return post;
    }

    @Test
    @DisplayName("확정 인원을 1 줄이고 줄어든 인원을 돌려준다 (예: 3/4 → 2/4)")
    void 확정_인원을_줄인다() {
        RecruitPost post = givenPost(3);

        assertThat(postCommandService.decreaseCurrentCount(POST_ID)).isEqualTo(2);
        assertThat(post.getCurrentCount()).isEqualTo(2);
        assertThat(post.getStatus()).isEqualTo(PostStatus.RECRUITING);
    }

    @Test
    @DisplayName("모집완료 글은 인원이 줄어도 모집중으로 돌아가지 않는다 (⑤, REC-007)")
    void 모집완료는_유지된다() {
        RecruitPost post = givenPost(4);
        post.complete(NOW.minusDays(1));

        assertThat(postCommandService.decreaseCurrentCount(POST_ID)).isEqualTo(3);
        assertThat(post.getStatus()).isEqualTo(PostStatus.COMPLETED);
    }

    @Test
    @DisplayName("삭제된 글도 팀은 남아 있으므로 줄인다 (PST-004 ④)")
    void 삭제된_글도_줄인다() {
        RecruitPost post = givenPost(3);
        post.delete(NOW.minusHours(1));

        assertThat(postCommandService.decreaseCurrentCount(POST_ID)).isEqualTo(2);
    }

    @Test
    @DisplayName("작성자만 남은 글(1명)은 더 줄일 수 없다 (팀장은 나갈 수 없음)")
    void 작성자만_남으면_줄이지_않는다() {
        RecruitPost post = givenPost(1);

        assertThrows(
                IllegalStateException.class,
                () -> postCommandService.decreaseCurrentCount(POST_ID));
        assertThat(post.getCurrentCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("모집글이 없으면 서버 오류 (팀은 항상 모집글이 있다)")
    void 모집글이_없으면_서버_오류() {
        given(recruitPostRepository.findByIdForUpdate(POST_ID)).willReturn(Optional.empty());

        assertThrows(
                IllegalStateException.class,
                () -> postCommandService.decreaseCurrentCount(POST_ID));
    }
}
