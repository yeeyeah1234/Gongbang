package com.gongbang.member.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.domain.*;
import com.gongbang.member.repository.MemberRepository;
import com.gongbang.notification.domain.NotificationType;
import com.gongbang.notification.service.NotificationService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AdminMemberServiceTest {
    final MemberRepository repository = mock(MemberRepository.class);
    final NotificationService notifications = mock(NotificationService.class);
    final AdminMemberService service = new AdminMemberService(repository, notifications);

    Member actor(MemberRole role) {
        Member m = mock(Member.class);
        when(m.isActive()).thenReturn(true);
        when(m.getRole()).thenReturn(role);
        when(repository.findById(1L)).thenReturn(Optional.of(m));
        return m;
    }

    @Test
    void 일반회원은_권한부여와_목록조회를_할수없다() {
        actor(MemberRole.USER);
        assertThatThrownBy(() -> service.grant(1L, 2L)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.list(1L, "", 0)).isInstanceOf(BusinessException.class);
        verify(repository, never()).findForRoleUpdate(any());
        verifyNoInteractions(notifications);
    }

    @Test
    void 모든관리자는_다른회원에게_동일한권한을부여한다() {
        actor(MemberRole.ADMIN);
        Member target = mock(Member.class);
        when(target.isActive()).thenReturn(true);
        when(target.getRole()).thenReturn(MemberRole.USER);
        when(repository.findForRoleUpdate(2L)).thenReturn(Optional.of(target));
        service.grant(1L, 2L);
        verify(target).grantAdmin();
    }

    @Test
    void 탈퇴회원은_권한을받지못한다() {
        actor(MemberRole.ADMIN);
        Member target = mock(Member.class);
        when(repository.findForRoleUpdate(2L)).thenReturn(Optional.of(target));
        assertThatThrownBy(() -> service.grant(1L, 2L)).isInstanceOf(BusinessException.class);
        verify(target, never()).grantAdmin();
    }

    @Test
    void 신규관리자를_제외한_활성관리자에게_부여자포함_사이트알림을보낸다() {
        Member grantingAdmin = actor(MemberRole.ADMIN);
        when(grantingAdmin.getId()).thenReturn(1L);
        Member target = mock(Member.class);
        when(target.getId()).thenReturn(2L);
        when(target.getNickname()).thenReturn("새관리자");
        when(target.isActive()).thenReturn(true);
        when(target.getRole()).thenReturn(MemberRole.USER);
        when(repository.findForRoleUpdate(2L)).thenReturn(Optional.of(target));
        Member other = mock(Member.class);
        when(other.getId()).thenReturn(3L);
        Member suspended = mock(Member.class);
        when(suspended.getId()).thenReturn(4L);
        when(suspended.isSuspended()).thenReturn(true);
        when(repository.findByRoleAndStatus(MemberRole.ADMIN, MemberStatus.ACTIVE))
                .thenReturn(List.of(grantingAdmin, target, other, suspended));

        service.grant(1L, 2L);

        for (Long receiver : List.of(1L, 3L)) {
            verify(notifications)
                    .send(
                            receiver,
                            NotificationType.ADMIN_GRANTED,
                            null,
                            "MEMBER",
                            2L,
                            "@새관리자 님이 관리자가 되었습니다!",
                            "/admin/members");
        }
        verifyNoMoreInteractions(notifications);
        assertThat(NotificationType.ADMIN_GRANTED.getSettingType()).isNull();
        assertThat(NotificationType.ADMIN_GRANTED.getSendMode()).isNull();
    }

    @Test
    void 이미관리자면_중복부여하지않는다() {
        actor(MemberRole.ADMIN);
        Member target = mock(Member.class);
        when(target.isActive()).thenReturn(true);
        when(target.getRole()).thenReturn(MemberRole.ADMIN);
        when(repository.findForRoleUpdate(2L)).thenReturn(Optional.of(target));
        service.grant(1L, 2L);
        verify(target, never()).grantAdmin();
        verifyNoInteractions(notifications);
    }

    @Test
    void 정지된관리자는_권한을부여할수없다() {
        when(actor(MemberRole.ADMIN).isSuspended()).thenReturn(true);
        assertThatThrownBy(() -> service.grant(1L, 2L)).isInstanceOf(BusinessException.class);
        verify(repository, never()).findForRoleUpdate(any());
    }

    @Test
    void 정지된회원은_관리자로승격할수없다() {
        actor(MemberRole.ADMIN);
        Member target = mock(Member.class);
        when(target.isActive()).thenReturn(true);
        when(target.isSuspended()).thenReturn(true);
        when(repository.findForRoleUpdate(2L)).thenReturn(Optional.of(target));
        assertThatThrownBy(() -> service.grant(1L, 2L)).isInstanceOf(BusinessException.class);
        verify(target, never()).grantAdmin();
    }

    @Test
    void 활성관리자여부_확인() {
        actor(MemberRole.ADMIN);
        assertThat(service.isAdmin(1L)).isTrue();

        Member suspendedAdmin = mock(Member.class);
        when(suspendedAdmin.isActive()).thenReturn(true);
        when(suspendedAdmin.isSuspended()).thenReturn(true);
        when(suspendedAdmin.getRole()).thenReturn(MemberRole.ADMIN);
        when(repository.findById(2L)).thenReturn(Optional.of(suspendedAdmin));
        assertThat(service.isAdmin(2L)).isFalse();

        Member user = mock(Member.class);
        when(user.isActive()).thenReturn(true);
        when(user.getRole()).thenReturn(MemberRole.USER);
        when(repository.findById(3L)).thenReturn(Optional.of(user));
        assertThat(service.isAdmin(3L)).isFalse();

        assertThat(service.isAdmin(null)).isFalse();
        assertThat(service.isAdmin(999L)).isFalse();
    }
}
