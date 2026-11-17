package com.gongbang.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.gongbang.application.domain.Application;
import com.gongbang.application.domain.ApplicationStatus;
import com.gongbang.application.dto.ApplicationCreateResponse;
import com.gongbang.application.repository.ApplicationRepository;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.post.domain.PostStatus;
import com.gongbang.post.domain.RecruitPost;
import com.gongbang.post.service.PostCommandService;
import com.gongbang.post.service.PostQueryService;
import com.gongbang.post.service.RecruitNotifier;
import com.gongbang.post.service.TeamJoinService;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

/** REC-001 지원하기. */
@ExtendWith(MockitoExtension.class)
class ApplicationCommandServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 13);
    private static final Long POST_ID = 10L;
    private static final Long AUTHOR_ID = 7L;
    private static final Long MEMBER_ID = 20L;

    @Mock private ApplicationRepository applicationRepository;
    @Mock private PostCommandService postCommandService;
    @Mock private PostQueryService postQueryService;
    @Mock private TeamJoinService teamJoinService;
    @Mock private RecruitNotifier recruitNotifier;

    private ApplicationCommandService applicationCommandService;

    @BeforeEach
    void setUp() {
        applicationCommandService =
                new ApplicationCommandService(
                        applicationRepository,
                        postCommandService,
                        postQueryService,
                        teamJoinService,
                        recruitNotifier);
    }

    /** 모집 기간 start~end, 상태 status인 글을 잠금 조회로 돌려준다. */
    private void givenPost(LocalDate start, LocalDate end, PostStatus status) {
        RecruitPost post =
                RecruitPost.withContest(AUTHOR_ID, 3L, "AI 팀원 모집", "내용", 4, start, end, null);
        ReflectionTestUtils.setField(post, "id", POST_ID);
        ReflectionTestUtils.setField(post, "status", status);
        given(postCommandService.lockPost(POST_ID)).willReturn(post);
    }

    private void givenRecruiting() {
        givenPost(TODAY.minusDays(1), TODAY.plusDays(7), PostStatus.RECRUITING);
    }

    private void givenSaveReturnsId(Long applicationId) {
        given(applicationRepository.saveAndFlush(any(Application.class)))
                .willAnswer(
                        invocation -> {
                            Application application = invocation.getArgument(0);
                            ReflectionTestUtils.setField(application, "id", applicationId);
                            return application;
                        });
    }

    private ErrorCode errorOf(Long memberId) {
        return assertThrows(
                        BusinessException.class,
                        () -> applicationCommandService.apply(memberId, POST_ID, TODAY))
                .getErrorCode();
    }

    @Test
    @DisplayName("모집중인 글에 지원하면 대기 상태 지원을 만들고 applicationId를 돌려준다")
    void 지원하면_대기_지원이_생긴다() {
        givenRecruiting();
        givenSaveReturnsId(55L);

        ApplicationCreateResponse response =
                applicationCommandService.apply(MEMBER_ID, POST_ID, TODAY);

        assertThat(response.applicationId()).isEqualTo(55L);
        ArgumentCaptor<Application> captor = ArgumentCaptor.forClass(Application.class);
        verify(applicationRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getPostId()).isEqualTo(POST_ID);
        assertThat(captor.getValue().getApplicantId()).isEqualTo(MEMBER_ID);
        assertThat(captor.getValue().getStatus()).isEqualTo(ApplicationStatus.PENDING);
    }

    @Test
    @DisplayName("본인 글에 지원하면 403 CANNOT_APPLY_OWN_POST")
    void 본인_글이면_403() {
        givenRecruiting();

        assertThat(errorOf(AUTHOR_ID)).isEqualTo(ErrorCode.CANNOT_APPLY_OWN_POST);
        verify(applicationRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("모집완료 글이면 409 POST_COMPLETED")
    void 모집완료_글이면_409() {
        givenPost(TODAY.minusDays(1), TODAY.plusDays(7), PostStatus.COMPLETED);

        assertThat(errorOf(MEMBER_ID)).isEqualTo(ErrorCode.POST_COMPLETED);
    }

    @Test
    @DisplayName("모집 종료일이 어제면 409 RECRUIT_PERIOD_ENDED")
    void 모집_기간이_끝났으면_409() {
        givenPost(TODAY.minusDays(7), TODAY.minusDays(1), PostStatus.RECRUITING);

        assertThat(errorOf(MEMBER_ID)).isEqualTo(ErrorCode.RECRUIT_PERIOD_ENDED);
    }

    @Test
    @DisplayName("모집 종료일 당일에는 지원할 수 있다")
    void 종료일_당일에는_지원할_수_있다() {
        givenPost(TODAY.minusDays(7), TODAY, PostStatus.RECRUITING);
        givenSaveReturnsId(56L);

        assertThat(applicationCommandService.apply(MEMBER_ID, POST_ID, TODAY).applicationId())
                .isEqualTo(56L);
    }

    @Test
    @DisplayName("모집 시작일 전에도 지원할 수 있다 (11/13 결정)")
    void 시작일_전에도_지원할_수_있다() {
        givenPost(TODAY.plusDays(7), TODAY.plusDays(14), PostStatus.RECRUITING);
        givenSaveReturnsId(57L);

        assertThat(applicationCommandService.apply(MEMBER_ID, POST_ID, TODAY).applicationId())
                .isEqualTo(57L);
    }

    @Test
    @DisplayName("이미 지원했으면(거절 포함) 409 ALREADY_APPLIED")
    void 이미_지원했으면_409() {
        givenRecruiting();
        given(applicationRepository.existsByPostIdAndApplicantId(POST_ID, MEMBER_ID))
                .willReturn(true);

        assertThat(errorOf(MEMBER_ID)).isEqualTo(ErrorCode.ALREADY_APPLIED);
        verify(applicationRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("확인 뒤 저장 전에 같은 지원이 먼저 들어와 UQ에 걸려도 409 ALREADY_APPLIED")
    void 동시에_같은_지원이_들어와도_409() {
        givenRecruiting();
        given(applicationRepository.saveAndFlush(any(Application.class)))
                .willThrow(new DataIntegrityViolationException("uk_application_post_applicant"));

        assertThat(errorOf(MEMBER_ID)).isEqualTo(ErrorCode.ALREADY_APPLIED);
    }

    @Test
    @DisplayName("없거나 삭제된 글이면 모집글 잠금 조회의 404 POST_NOT_FOUND를 그대로 보낸다")
    void 없는_글이면_404() {
        given(postCommandService.lockPost(POST_ID))
                .willThrow(new BusinessException(ErrorCode.POST_NOT_FOUND));

        assertThat(errorOf(MEMBER_ID)).isEqualTo(ErrorCode.POST_NOT_FOUND);
    }
}
