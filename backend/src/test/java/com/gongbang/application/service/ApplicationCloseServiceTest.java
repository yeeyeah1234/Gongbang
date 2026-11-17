package com.gongbang.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.gongbang.application.domain.Application;
import com.gongbang.application.domain.ApplicationStatus;
import com.gongbang.application.repository.ApplicationRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** PST-004 ④ 모집글 삭제 때 대기 지원 종료. */
@ExtendWith(MockitoExtension.class)
class ApplicationCloseServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 13, 16, 30);
    private static final Long POST_ID = 10L;

    @Mock private ApplicationRepository applicationRepository;

    private ApplicationCloseService applicationCloseService;

    @BeforeEach
    void setUp() {
        applicationCloseService = new ApplicationCloseService(applicationRepository);
    }

    @Test
    @DisplayName("잠근 대기 지원을 모두 종료(CLOSED)하고 처리 시각을 남기며, 알림용으로 지원자 ID를 돌려준다")
    void 대기_지원을_모두_종료한다() {
        Application first = Application.pending(POST_ID, 20L);
        Application second = Application.pending(POST_ID, 21L);
        given(
                        applicationRepository.findByPostIdAndStatusForUpdate(
                                POST_ID, ApplicationStatus.PENDING))
                .willReturn(List.of(first, second));

        List<Long> applicantIds = applicationCloseService.closePendingApplications(POST_ID, NOW);

        assertThat(applicantIds).containsExactly(20L, 21L);
        assertThat(List.of(first, second))
                .allSatisfy(
                        application -> {
                            assertThat(application.getStatus()).isEqualTo(ApplicationStatus.CLOSED);
                            assertThat(application.getProcessedAt()).isEqualTo(NOW);
                            assertThat(application.getRejectType()).isNull();
                        });
    }

    @Test
    @DisplayName("대기 지원이 없으면 아무것도 바꾸지 않고 빈 목록을 돌려준다")
    void 대기_지원이_없으면_빈_목록() {
        given(
                        applicationRepository.findByPostIdAndStatusForUpdate(
                                POST_ID, ApplicationStatus.PENDING))
                .willReturn(List.of());

        assertThat(applicationCloseService.closePendingApplications(POST_ID, NOW)).isEmpty();
    }
}
