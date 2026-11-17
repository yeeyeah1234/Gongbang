package com.gongbang.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.gongbang.application.domain.Application;
import com.gongbang.application.domain.ApplicationStatus;
import com.gongbang.application.domain.RejectType;
import com.gongbang.application.repository.ApplicationRepository;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.post.domain.RecruitPost;
import com.gongbang.post.service.PostCommandService;
import com.gongbang.post.service.PostMembershipService;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** REC-005 지원자 거절. */
@ExtendWith(MockitoExtension.class)
class ApplicationRejectServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 13, 16, 0);
    private static final Long APPLICATION_ID = 55L;
    private static final Long POST_ID = 10L;
    private static final Long AUTHOR_ID = 7L;
    private static final Long APPLICANT_ID = 20L;

    @Mock private ApplicationRepository applicationRepository;
    @Mock private PostCommandService postCommandService;
    @Mock private PostQueryService postQueryService;
    @Mock private TeamJoinService teamJoinService;
    @Mock private RecruitNotifier recruitNotifier;
    @Mock private PostMembershipService postMembershipService;

    private ApplicationCommandService applicationCommandService;

    @BeforeEach
    void setUp() {
        applicationCommandService =
                new ApplicationCommandService(
                        applicationRepository,
                        postCommandService,
                        postQueryService,
                        teamJoinService,
                        recruitNotifier,
                        postMembershipService);
    }

    private Application givenApplication(ApplicationStatus status) {
        Application application = Application.pending(POST_ID, APPLICANT_ID);
        ReflectionTestUtils.setField(application, "id", APPLICATION_ID);
        ReflectionTestUtils.setField(application, "status", status);
        given(applicationRepository.findByIdForUpdate(APPLICATION_ID))
                .willReturn(Optional.of(application));
        return application;
    }

    private void givenPost() {
        LocalDate start = NOW.toLocalDate();
        RecruitPost post =
                RecruitPost.withContest(
                        AUTHOR_ID, 3L, "제목", "내용", 4, start, start.plusDays(7), null);
        ReflectionTestUtils.setField(post, "id", POST_ID);
        given(postQueryService.getActivePost(POST_ID)).willReturn(post);
    }

    private ErrorCode errorOf(Long memberId) {
        return assertThrows(
                        BusinessException.class,
                        () -> applicationCommandService.reject(memberId, APPLICATION_ID, NOW))
                .getErrorCode();
    }

    @Test
    @DisplayName("작성자가 대기 지원을 거절하면 거절(MANUAL)로 바꾸고 처리 시각을 남긴다. 행은 남아 다시 지원할 수 없다")
    void 대기_지원을_거절한다() {
        Application application = givenApplication(ApplicationStatus.PENDING);
        givenPost();

        applicationCommandService.reject(AUTHOR_ID, APPLICATION_ID, NOW);

        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
        assertThat(application.getRejectType()).isEqualTo(RejectType.MANUAL);
        assertThat(application.getProcessedAt()).isEqualTo(NOW);
        verify(applicationRepository, never()).delete(application);
        // ④, INT-003 ③: 지원자에게 거절 알림 (보낸 사람은 작성자)
        verify(recruitNotifier)
                .rejected(
                        org.mockito.ArgumentMatchers.argThat(post -> POST_ID.equals(post.getId())),
                        org.mockito.ArgumentMatchers.eq(APPLICANT_ID),
                        org.mockito.ArgumentMatchers.eq(AUTHOR_ID));
    }

    @Test
    @DisplayName("거절은 모집 인원을 바꾸지 않고 모집글 행도 잠그지 않는다 (수락과의 교착 방지)")
    void 거절은_모집글을_잠그지_않는다() {
        givenApplication(ApplicationStatus.PENDING);
        givenPost();

        applicationCommandService.reject(AUTHOR_ID, APPLICATION_ID, NOW);

        verifyNoInteractions(postCommandService);
    }

    @Test
    @DisplayName("작성자가 아니면 403 NOT_POST_AUTHOR이고 상태를 바꾸지 않는다")
    void 작성자가_아니면_403() {
        Application application = givenApplication(ApplicationStatus.PENDING);
        givenPost();

        assertThat(errorOf(APPLICANT_ID)).isEqualTo(ErrorCode.NOT_POST_AUTHOR);
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.PENDING);
    }

    @ParameterizedTest
    @EnumSource(
            value = ApplicationStatus.class,
            names = {"ACCEPTED", "REJECTED", "CLOSED"})
    @DisplayName("이미 처리된 지원이면 409 ALREADY_PROCESSED")
    void 처리된_지원이면_409(ApplicationStatus status) {
        givenApplication(status);
        givenPost();

        assertThat(errorOf(AUTHOR_ID)).isEqualTo(ErrorCode.ALREADY_PROCESSED);
    }

    @Test
    @DisplayName("지원자가 그 사이 취소해 없는 지원이면 404 APPLICATION_NOT_FOUND")
    void 없는_지원이면_404() {
        given(applicationRepository.findByIdForUpdate(APPLICATION_ID)).willReturn(Optional.empty());

        assertThat(errorOf(AUTHOR_ID)).isEqualTo(ErrorCode.APPLICATION_NOT_FOUND);
    }

    @Test
    @DisplayName("삭제된 글의 지원이면 모집글 조회의 404 POST_NOT_FOUND를 그대로 보낸다")
    void 삭제된_글이면_404() {
        givenApplication(ApplicationStatus.PENDING);
        given(postQueryService.getActivePost(POST_ID))
                .willThrow(new BusinessException(ErrorCode.POST_NOT_FOUND));

        assertThat(errorOf(AUTHOR_ID)).isEqualTo(ErrorCode.POST_NOT_FOUND);
    }
}
