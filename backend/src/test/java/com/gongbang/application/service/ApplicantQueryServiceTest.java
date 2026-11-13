package com.gongbang.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.gongbang.application.domain.Application;
import com.gongbang.application.domain.ApplicationStatus;
import com.gongbang.application.dto.ApplicantListResponse;
import com.gongbang.application.repository.ApplicationRepository;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.post.domain.RecruitPost;
import com.gongbang.post.service.PostQueryService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** REC-003 지원자 목록. */
@ExtendWith(MockitoExtension.class)
class ApplicantQueryServiceTest {

    private static final Long POST_ID = 10L;
    private static final Long AUTHOR_ID = 7L;

    @Mock private ApplicationRepository applicationRepository;
    @Mock private PostQueryService postQueryService;

    private ApplicantQueryService applicantQueryService;

    @BeforeEach
    void setUp() {
        applicantQueryService = new ApplicantQueryService(applicationRepository, postQueryService);
    }

    private void givenPost() {
        LocalDate start = LocalDate.of(2026, 11, 13);
        RecruitPost post =
                RecruitPost.withContest(
                        AUTHOR_ID, 3L, "제목", "내용", 4, start, start.plusDays(7), null);
        ReflectionTestUtils.setField(post, "id", POST_ID);
        given(postQueryService.getActivePost(POST_ID)).willReturn(post);
    }

    private static Application application(Long id, Long applicantId, LocalDateTime createdAt) {
        Application application = Application.pending(POST_ID, applicantId);
        ReflectionTestUtils.setField(application, "id", id);
        ReflectionTestUtils.setField(application, "createdAt", createdAt);
        return application;
    }

    private ErrorCode errorOf(Long memberId, String status) {
        return assertThrows(
                        BusinessException.class,
                        () -> applicantQueryService.getApplicants(memberId, POST_ID, status))
                .getErrorCode();
    }

    @Test
    @DisplayName("작성자는 탭별 인원 수와 고른 탭의 지원자를 저장소가 준 순서(먼저 지원한 순) 그대로 받는다")
    void 작성자는_탭별_인원과_지원자를_본다() {
        givenPost();
        LocalDateTime first = LocalDateTime.of(2026, 11, 13, 9, 0);
        given(
                        applicationRepository.findByPostIdAndStatusOrderByCreatedAtAscIdAsc(
                                POST_ID, ApplicationStatus.PENDING))
                .willReturn(
                        List.of(
                                application(1L, 20L, first),
                                application(2L, 21L, first.plusMinutes(5))));
        given(applicationRepository.countByStatus(POST_ID))
                .willReturn(
                        List.of(
                                new Object[] {ApplicationStatus.PENDING, 2L},
                                new Object[] {ApplicationStatus.REJECTED, 1L}));

        ApplicantListResponse response =
                applicantQueryService.getApplicants(AUTHOR_ID, POST_ID, "PENDING");

        assertThat(response.counts().pending()).isEqualTo(2);
        assertThat(response.counts().accepted()).isZero();
        assertThat(response.counts().rejected()).isEqualTo(1);
        assertThat(response.items())
                .extracting(ApplicantListResponse.Item::applicationId)
                .containsExactly(1L, 2L);
        assertThat(response.items().get(0).member().memberId()).isEqualTo(20L);
        assertThat(response.items().get(0).createdAt()).isEqualTo(first);
    }

    @Test
    @DisplayName("status를 보내지 않으면 대기(PENDING) 탭을 준다")
    void 상태가_없으면_대기_탭() {
        givenPost();
        given(applicationRepository.countByStatus(POST_ID)).willReturn(List.of());

        applicantQueryService.getApplicants(AUTHOR_ID, POST_ID, null);

        verify(applicationRepository)
                .findByPostIdAndStatusOrderByCreatedAtAscIdAsc(POST_ID, ApplicationStatus.PENDING);
    }

    @Test
    @DisplayName("지원자가 없으면 빈 목록과 인원 수 0을 준다")
    void 지원자가_없으면_빈_목록() {
        givenPost();
        given(applicationRepository.countByStatus(POST_ID)).willReturn(List.of());

        ApplicantListResponse response =
                applicantQueryService.getApplicants(AUTHOR_ID, POST_ID, "ACCEPTED");

        assertThat(response.items()).isEmpty();
        assertThat(response.counts())
                .isEqualTo(new ApplicantListResponse.Counts(0, 0, 0));
    }

    @Test
    @DisplayName("작성자가 아니면 403 NOT_POST_AUTHOR이고 지원자를 읽지 않는다")
    void 작성자가_아니면_403() {
        givenPost();

        assertThat(errorOf(99L, "PENDING")).isEqualTo(ErrorCode.NOT_POST_AUTHOR);
        verify(applicationRepository, never())
                .findByPostIdAndStatusOrderByCreatedAtAscIdAsc(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CLOSED", "pending", "ALL", ""})
    @DisplayName("탭이 PENDING·ACCEPTED·REJECTED가 아니면 400 INVALID_INPUT")
    void 잘못된_탭이면_400(String status) {
        assertThat(errorOf(AUTHOR_ID, status)).isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("없거나 삭제된 글이면 모집글 조회의 404 POST_NOT_FOUND를 그대로 보낸다")
    void 없는_글이면_404() {
        given(postQueryService.getActivePost(POST_ID))
                .willThrow(new BusinessException(ErrorCode.POST_NOT_FOUND));

        assertThat(errorOf(AUTHOR_ID, "PENDING")).isEqualTo(ErrorCode.POST_NOT_FOUND);
    }
}
