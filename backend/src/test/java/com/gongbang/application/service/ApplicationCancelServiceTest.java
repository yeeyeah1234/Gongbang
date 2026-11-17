package com.gongbang.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.gongbang.application.domain.Application;
import com.gongbang.application.domain.ApplicationStatus;
import com.gongbang.application.repository.ApplicationRepository;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.post.service.PostCommandService;
import com.gongbang.post.service.PostQueryService;
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

/** REC-002 지원 취소. */
@ExtendWith(MockitoExtension.class)
class ApplicationCancelServiceTest {

    private static final Long APPLICATION_ID = 55L;
    private static final Long POST_ID = 10L;
    private static final Long APPLICANT_ID = 20L;

    @Mock private ApplicationRepository applicationRepository;
    @Mock private PostCommandService postCommandService;
    @Mock private PostQueryService postQueryService;

    private ApplicationCommandService applicationCommandService;

    @BeforeEach
    void setUp() {
        applicationCommandService =
                new ApplicationCommandService(
                        applicationRepository, postCommandService, postQueryService);
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
                        () -> applicationCommandService.cancel(memberId, APPLICATION_ID))
                .getErrorCode();
    }

    @Test
    @DisplayName("본인의 대기 지원을 취소하면 지원 행을 지운다 (다시 지원 가능)")
    void 대기_지원을_취소하면_지운다() {
        Application application = givenApplication(ApplicationStatus.PENDING);

        applicationCommandService.cancel(APPLICANT_ID, APPLICATION_ID);

        verify(applicationRepository).delete(application);
    }

    @Test
    @DisplayName("다른 사람의 지원이면 403 NOT_APPLICANT이고 지우지 않는다")
    void 남의_지원이면_403() {
        givenApplication(ApplicationStatus.PENDING);

        assertThat(errorOf(99L)).isEqualTo(ErrorCode.NOT_APPLICANT);
        verify(applicationRepository, never()).delete(any());
    }

    @ParameterizedTest
    @EnumSource(
            value = ApplicationStatus.class,
            names = {"ACCEPTED", "REJECTED", "CLOSED"})
    @DisplayName("이미 수락·거절·종료된 지원이면 409 ALREADY_PROCESSED이고 지우지 않는다")
    void 처리된_지원이면_409(ApplicationStatus status) {
        givenApplication(status);

        assertThat(errorOf(APPLICANT_ID)).isEqualTo(ErrorCode.ALREADY_PROCESSED);
        verify(applicationRepository, never()).delete(any());
    }

    @Test
    @DisplayName("이미 취소해 없는 지원이면 404 APPLICATION_NOT_FOUND")
    void 없는_지원이면_404() {
        given(applicationRepository.findByIdForUpdate(APPLICATION_ID)).willReturn(Optional.empty());

        assertThat(errorOf(APPLICANT_ID)).isEqualTo(ErrorCode.APPLICATION_NOT_FOUND);
    }
}
