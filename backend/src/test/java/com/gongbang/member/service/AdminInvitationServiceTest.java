package com.gongbang.member.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.domain.AdminInvitation;
import com.gongbang.member.domain.Member;
import com.gongbang.member.repository.AdminInvitationRepository;
import com.gongbang.member.repository.MemberRepository;
import com.gongbang.notification.domain.NotificationType;
import com.gongbang.notification.service.NotificationService;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AdminInvitationServiceTest {
    final AdminInvitationRepository invitations = mock(AdminInvitationRepository.class);
    final MemberRepository members = mock(MemberRepository.class);
    final AdminMemberService admins = mock(AdminMemberService.class);
    final NotificationService notifications = mock(NotificationService.class);
    final com.gongbang.admin.service.AdminActivityWriter audit =
            mock(com.gongbang.admin.service.AdminActivityWriter.class);
    final AdminInvitationService service =
            new AdminInvitationService(audit, invitations, members, admins, notifications);
    final Member target = mock(Member.class);
    final AdminInvitation invitation = new AdminInvitation(1L, 2L, LocalDateTime.now());

    @BeforeEach
    void setup() {
        when(target.isActive()).thenReturn(true);
        when(members.findForRoleUpdate(2L)).thenReturn(Optional.of(target));
        ReflectionTestUtils.setField(invitation, "id", 10L);
        when(invitations.findById(10L)).thenReturn(Optional.of(invitation));
        when(invitations.save(any())).thenReturn(invitation);
    }

    @Test
    void 초대만으로는권한을주지않고_본인알림만보낸다() {
        assertThat(service.invite(1L, 2L).status()).isEqualTo("PENDING");
        verify(admins, never()).grant(any(), any());
        verify(notifications)
                .send(
                        eq(2L),
                        eq(NotificationType.ADMIN_INVITE),
                        eq(1L),
                        eq("ADMIN_INVITATION"),
                        eq(10L),
                        anyString(),
                        eq("/notifications"));
    }

    @Test
    void 대기초대가있으면_재발송하지않는다() {
        when(invitations.findByInviteeIdAndStatus(2L, "PENDING"))
                .thenReturn(Optional.of(invitation));
        assertThat(service.invite(1L, 2L).invitationId()).isEqualTo(10L);
        verify(invitations, never()).save(any());
        verifyNoInteractions(notifications, audit);
    }

    @Test
    void 본인수락시에만_한번권한을부여한다() {
        service.respond(2L, 10L, true);
        service.respond(2L, 10L, true);
        verify(admins, times(1)).grant(1L, 2L);
        verify(audit, times(1)).record(2L, "ADMIN_INVITE_ACCEPT", "ADMIN_INVITATION", 10L, null);
        assertThat(invitation.getStatus()).isEqualTo("ACCEPTED");
        assertThat(invitation.getRespondedAt()).isNotNull();
    }

    @Test
    void 거절하면권한없이종료하고_뒤늦은수락을거부한다() {
        service.respond(2L, 10L, false);
        assertThatThrownBy(() -> service.respond(2L, 10L, true))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(admins);
        assertThat(invitation.getStatus()).isEqualTo("DECLINED");
    }

    @Test
    void 다른회원의초대는수락할수없다() {
        when(members.findForRoleUpdate(3L)).thenReturn(Optional.of(target));
        assertThatThrownBy(() -> service.respond(3L, 10L, true))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(admins);
        assertThat(invitation.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void 권한부여실패시_수락으로바꾸지않는다() {
        doThrow(new IllegalStateException()).when(admins).grant(1L, 2L);
        assertThatThrownBy(() -> service.respond(2L, 10L, true))
                .isInstanceOf(IllegalStateException.class);
        assertThat(invitation.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void 정지회원은수락할수없다() {
        when(target.isSuspended()).thenReturn(true);
        assertThatThrownBy(() -> service.respond(2L, 10L, true))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(admins);
    }
}
