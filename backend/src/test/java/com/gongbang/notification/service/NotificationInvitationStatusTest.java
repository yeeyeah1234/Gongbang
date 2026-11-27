package com.gongbang.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gongbang.invitation.service.InvitationQueryService;
import com.gongbang.member.service.MemberService;
import com.gongbang.notification.domain.Notification;
import com.gongbang.notification.domain.NotificationType;
import com.gongbang.notification.repository.NotificationRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;

class NotificationInvitationStatusTest {
    private final NotificationRepository repository = mock(NotificationRepository.class);
    private final InvitationQueryService invitations = mock(InvitationQueryService.class);
    private final NotificationQueryService service =
            new NotificationQueryService(
                    repository,
                    mock(MemberService.class),
                    invitations,
                    mock(com.gongbang.member.service.AdminInvitationService.class));

    private Notification invitation(long id) {
        return new Notification(
                1L,
                NotificationType.TEAM_INVITE,
                null,
                "INVITATION",
                id,
                "팀 초대",
                "/notifications",
                LocalDateTime.now());
    }

    @Test
    void 실제상태를_한번에조회하고_없는초대는_대기로표시하지않는다() {
        when(repository.findPage(eq(1L), eq(false), anyList(), any()))
                .thenReturn(
                        new PageImpl<>(
                                List.of(
                                        invitation(2),
                                        invitation(2),
                                        invitation(3),
                                        invitation(4))));
        when(invitations.findStatuses(List.of(2L, 3L, 4L)))
                .thenReturn(Map.of(2L, "DECLINED", 3L, "CANCELED"));
        var items = service.list(1L, 0, 20, false, null).items();
        assertThat(items.get(0).invitation().status()).isEqualTo("DECLINED");
        assertThat(items.get(1).invitation().status()).isEqualTo("DECLINED");
        assertThat(items.get(2).invitation().status()).isEqualTo("CANCELED");
        assertThat(items.get(3).invitation()).isNull();
        verify(invitations).findStatuses(List.of(2L, 3L, 4L));
        verifyNoMoreInteractions(invitations);
    }

    @Test
    void 초대가없는목록은_초대서비스를조회하지않는다() {
        when(repository.findPage(eq(1L), eq(false), anyList(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        assertThat(service.list(1L, 0, 20, false, null).items()).isEmpty();
        verifyNoInteractions(invitations);
    }
}
