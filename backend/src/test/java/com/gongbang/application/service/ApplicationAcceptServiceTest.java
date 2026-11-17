package com.gongbang.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.gongbang.application.domain.Application;
import com.gongbang.application.domain.ApplicationStatus;
import com.gongbang.application.dto.ApplicationAcceptResponse;
import com.gongbang.application.repository.ApplicationRepository;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.post.domain.PostStatus;
import com.gongbang.post.domain.RecruitPost;
import com.gongbang.post.dto.TeamJoinResult;
import com.gongbang.post.service.PostCommandService;
import com.gongbang.post.service.PostQueryService;
import com.gongbang.post.service.RecruitNotifier;
import com.gongbang.post.service.TeamJoinService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** REC-004 지원자 수락. */
@ExtendWith(MockitoExtension.class)
class ApplicationAcceptServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 17, 20, 0);
    private static final Long APPLICATION_ID = 55L;
    private static final Long POST_ID = 10L;
    private static final Long AUTHOR_ID = 7L;
    private static final Long APPLICANT_ID = 20L;

    @Mock private ApplicationRepository applicationRepository;
    @Mock private PostCommandService postCommandService;
    @Mock private PostQueryService postQueryService;
    @Mock private TeamJoinService teamJoinService;
    @Mock private RecruitNotifier recruitNotifier;

    private ApplicationCommandService applicationCommandService;
    private RecruitPost post;

    @BeforeEach
    void setUp() {
        applicationCommandService =
                new ApplicationCommandService(
                        applicationRepository,
                        postCommandService,
                        postQueryService,
                        teamJoinService,
                        recruitNotifier);
        LocalDate start = NOW.toLocalDate();
        post =
                RecruitPost.withContest(
                        AUTHOR_ID, 3L, "AI 팀원 모집", "내용", 4, start, start.plusDays(7), null);
        ReflectionTestUtils.setField(post, "id", POST_ID);
    }

    private void givenPostLocked() {
        given(applicationRepository.findPostIdById(APPLICATION_ID))
                .willReturn(Optional.of(POST_ID));
        given(postCommandService.lockPost(POST_ID)).willReturn(post);
    }

    private Application givenApplication(ApplicationStatus status) {
        Application application = Application.pending(POST_ID, APPLICANT_ID);
        ReflectionTestUtils.setField(application, "id", APPLICATION_ID);
        ReflectionTestUtils.setField(application, "status", status);
        given(applicationRepository.findByIdForUpdate(APPLICATION_ID))
                .willReturn(Optional.of(application));
        return application;
    }

    private ErrorCode errorOf(Long memberId) {
        return assertThrows(
                        BusinessException.class,
                        () -> applicationCommandService.accept(memberId, APPLICATION_ID, NOW))
                .getErrorCode();
    }

    @Test
    @DisplayName("작성자가 대기 지원을 수락하면 수락으로 바꾸고 확정 팀원으로 등록하며, 지원자에게 수락 알림을 보낸다")
    void 대기_지원을_수락한다() {
        givenPostLocked();
        Application application = givenApplication(ApplicationStatus.PENDING);
        given(teamJoinService.join(POST_ID, APPLICANT_ID, NOW))
                .willReturn(new TeamJoinResult(300L, 2, 4, PostStatus.RECRUITING));

        ApplicationAcceptResponse response =
                applicationCommandService.accept(AUTHOR_ID, APPLICATION_ID, NOW);

        assertThat(response)
                .isEqualTo(new ApplicationAcceptResponse(300L, 2, 4, PostStatus.RECRUITING));
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.ACCEPTED);
        assertThat(application.getProcessedAt()).isEqualTo(NOW);
        verify(recruitNotifier).accepted(post, APPLICANT_ID, AUTHOR_ID);
    }

    @Test
    @DisplayName("모집글을 먼저 잠그고 지원을 잠근다 (수정·삭제·지원하기와 같은 잠금 순서)")
    void 모집글을_먼저_잠그고_지원을_잠근다() {
        givenPostLocked();
        givenApplication(ApplicationStatus.PENDING);
        given(teamJoinService.join(POST_ID, APPLICANT_ID, NOW))
                .willReturn(new TeamJoinResult(300L, 2, 4, PostStatus.RECRUITING));

        applicationCommandService.accept(AUTHOR_ID, APPLICATION_ID, NOW);

        InOrder order = inOrder(applicationRepository, postCommandService, teamJoinService);
        order.verify(applicationRepository).findPostIdById(APPLICATION_ID);
        order.verify(postCommandService).lockPost(POST_ID);
        order.verify(applicationRepository).findByIdForUpdate(APPLICATION_ID);
        order.verify(teamJoinService).join(POST_ID, APPLICANT_ID, NOW);
    }

    @Test
    @DisplayName("정원에 닿아 자동 모집완료되면 응답의 postStatus가 COMPLETED")
    void 정원에_닿으면_모집완료로_응답한다() {
        givenPostLocked();
        givenApplication(ApplicationStatus.PENDING);
        given(teamJoinService.join(POST_ID, APPLICANT_ID, NOW))
                .willReturn(new TeamJoinResult(300L, 4, 4, PostStatus.COMPLETED));

        assertThat(applicationCommandService.accept(AUTHOR_ID, APPLICATION_ID, NOW).postStatus())
                .isEqualTo(PostStatus.COMPLETED);
    }

    @Test
    @DisplayName("작성자가 아니면 403 NOT_POST_AUTHOR이고 지원을 잠그지도 팀에 넣지도 않는다")
    void 작성자가_아니면_403() {
        givenPostLocked();

        assertThat(errorOf(APPLICANT_ID)).isEqualTo(ErrorCode.NOT_POST_AUTHOR);
        verify(applicationRepository, never()).findByIdForUpdate(any());
        verifyNoInteractions(teamJoinService, recruitNotifier);
    }

    @ParameterizedTest
    @EnumSource(
            value = ApplicationStatus.class,
            names = {"ACCEPTED", "REJECTED", "CLOSED"})
    @DisplayName("이미 처리된 지원이면 409 ALREADY_PROCESSED")
    void 처리된_지원이면_409(ApplicationStatus status) {
        givenPostLocked();
        givenApplication(status);

        assertThat(errorOf(AUTHOR_ID)).isEqualTo(ErrorCode.ALREADY_PROCESSED);
        verifyNoInteractions(teamJoinService, recruitNotifier);
    }

    @Test
    @DisplayName("없는 지원이면 404 APPLICATION_NOT_FOUND이고 모집글을 잠그지 않는다")
    void 없는_지원이면_404() {
        given(applicationRepository.findPostIdById(APPLICATION_ID)).willReturn(Optional.empty());

        assertThat(errorOf(AUTHOR_ID)).isEqualTo(ErrorCode.APPLICATION_NOT_FOUND);
        verifyNoInteractions(postCommandService);
    }

    @Test
    @DisplayName("모집글을 잠그는 사이 지원자가 취소했으면 404 APPLICATION_NOT_FOUND")
    void 잠그는_사이_취소됐으면_404() {
        givenPostLocked();
        given(applicationRepository.findByIdForUpdate(APPLICATION_ID)).willReturn(Optional.empty());

        assertThat(errorOf(AUTHOR_ID)).isEqualTo(ErrorCode.APPLICATION_NOT_FOUND);
    }

    @Test
    @DisplayName("정원이 찼으면 확정 처리의 409 POST_FULL을 그대로 보내고 수락 알림을 보내지 않는다")
    void 정원이_찼으면_POST_FULL() {
        givenPostLocked();
        givenApplication(ApplicationStatus.PENDING);
        given(teamJoinService.join(POST_ID, APPLICANT_ID, NOW))
                .willThrow(new BusinessException(ErrorCode.POST_FULL));

        assertThat(errorOf(AUTHOR_ID)).isEqualTo(ErrorCode.POST_FULL);
        verifyNoInteractions(recruitNotifier);
    }

    @Test
    @DisplayName("삭제된 글의 지원이면 모집글 잠금 조회의 404 POST_NOT_FOUND를 그대로 보낸다")
    void 삭제된_글이면_404() {
        given(applicationRepository.findPostIdById(APPLICATION_ID))
                .willReturn(Optional.of(POST_ID));
        given(postCommandService.lockPost(POST_ID))
                .willThrow(new BusinessException(ErrorCode.POST_NOT_FOUND));

        assertThat(errorOf(AUTHOR_ID)).isEqualTo(ErrorCode.POST_NOT_FOUND);
    }
}
