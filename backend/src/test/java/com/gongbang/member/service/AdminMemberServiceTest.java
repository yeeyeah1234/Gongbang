package com.gongbang.member.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.domain.*;
import com.gongbang.member.repository.MemberRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AdminMemberServiceTest {
    final MemberRepository repository = mock(MemberRepository.class);
    final AdminMemberService service = new AdminMemberService(repository);

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
    }

    @Test
    void 관리자도_다른회원에게_권한을부여하지못한다() {
        actor(MemberRole.ADMIN);
        Member target = mock(Member.class);
        when(target.isActive()).thenReturn(true);
        when(target.getRole()).thenReturn(MemberRole.USER);
        when(repository.findForRoleUpdate(2L)).thenReturn(Optional.of(target));
        assertThatThrownBy(() -> service.grant(1L, 2L)).isInstanceOf(BusinessException.class);
        verify(target, never()).grantAdmin();
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
    void 이미관리자면_중복부여하지않는다() {
        actor(MemberRole.ADMIN);
        Member target = mock(Member.class);
        when(target.isActive()).thenReturn(true);
        when(target.getRole()).thenReturn(MemberRole.ADMIN);
        when(repository.findForRoleUpdate(2L)).thenReturn(Optional.of(target));
        assertThatThrownBy(() -> service.grant(1L, 2L)).isInstanceOf(BusinessException.class);
        verify(target, never()).grantAdmin();
    }
}
