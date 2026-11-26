package com.gongbang.invitation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.gongbang.invitation.domain.InvitationStatus;
import com.gongbang.invitation.domain.TeamInvitation;
import com.gongbang.invitation.repository.TeamInvitationRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvitationCancelServiceTest {

    @Mock private TeamInvitationRepository teamInvitationRepository;
    @InjectMocks private InvitationCancelService cancelService;

    @Test
    @DisplayName("대기 중인 초대만 CANCELED로 바꾸고 취소된 대상자 ID 목록을 돌려주며, 반복 호출해도 안전하다")
    void 대기초대_일괄취소_및_멱등성() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 26, 19, 0);
        TeamInvitation first = TeamInvitation.create(10L, 1L, 2L, now.minusHours(1));
        TeamInvitation second = TeamInvitation.create(10L, 1L, 3L, now.minusMinutes(30));

        given(
                        teamInvitationRepository.findByPostIdAndStatusForUpdate(
                                10L, InvitationStatus.PENDING))
                .willReturn(List.of(first, second))
                .willReturn(List.of());

        List<Long> canceledFirst = cancelService.cancelPendingByPostId(10L, now);
        assertThat(canceledFirst).containsExactly(2L, 3L);
        assertThat(first.getStatus()).isEqualTo(InvitationStatus.CANCELED);
        assertThat(first.getRespondedAt()).isEqualTo(now);
        assertThat(second.getStatus()).isEqualTo(InvitationStatus.CANCELED);

        List<Long> canceledSecond = cancelService.cancelPendingByPostId(10L, now.plusMinutes(1));
        assertThat(canceledSecond).isEmpty();
        verify(teamInvitationRepository, org.mockito.Mockito.times(2))
                .findByPostIdAndStatusForUpdate(10L, InvitationStatus.PENDING);
    }

    @Test
    @DisplayName("postId가 null이면 DB를 조회하지 않고 빈 목록을 돌려준다")
    void postId_null이면_빈목록() {
        assertThat(cancelService.cancelPendingByPostId(null, LocalDateTime.now())).isEmpty();
        org.mockito.Mockito.verifyNoInteractions(teamInvitationRepository);
    }
}
