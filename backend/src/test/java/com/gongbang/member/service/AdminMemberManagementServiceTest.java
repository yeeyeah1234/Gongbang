package com.gongbang.member.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.domain.Member;
import com.gongbang.member.repository.MemberRepository;
import com.gongbang.notification.service.NotificationService;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AdminMemberManagementServiceTest {
    final MemberRepository repository = mock(MemberRepository.class);
    final AdminMemberService access = mock(AdminMemberService.class);
    final NotificationService notifications = mock(NotificationService.class);
    final AdminMemberManagementService service =
            new AdminMemberManagementService(
                    mock(com.gongbang.admin.service.AdminActivityWriter.class),
                    repository,
                    access,
                    notifications,
                    mock(EntityManager.class),
                    mock(com.gongbang.auth.service.AuthService.class));

    Member target() {
        var m = Member.createEmailMember("member@example.com", "hash", "회원");
        ReflectionTestUtils.setField(m, "id", 2L);
        when(repository.findForRoleUpdate(2L)).thenReturn(Optional.of(m));
        return m;
    }

    @Test
    void 잠금해제는_실패횟수를초기화하되_이용정지를해제하지않는다() {
        var m = target();
        var until = LocalDateTime.now().plusDays(3);
        m.moderate("SUSPEND", "사유", until, 1L, LocalDateTime.now());
        for (int i = 0; i < 10; i++) m.increaseLoginFail(LocalDateTime.now());
        service.act(1L, 2L, new AdminMemberManagementService.Action("UNLOCK", null, null));
        assertThat(m.getLockedUntil()).isNull();
        assertThat(m.getLoginFailCount()).isZero();
        assertThat(m.getSuspendedUntil()).isEqualTo(until);
    }

    @Test
    void 관리자권한부여를_차단한다() {
        var m = target();
        assertThatThrownBy(
                        () ->
                                service.act(
                                        1L,
                                        2L,
                                        new AdminMemberManagementService.Action(
                                                "GRANT_ADMIN", null, null)))
                .isInstanceOf(BusinessException.class);
        assertThat(m.isAdmin()).isFalse();
    }

    @Test
    void 과거시각으로_정지하지않는다() {
        var m = target();
        assertThatThrownBy(
                        () ->
                                service.act(
                                        1L,
                                        2L,
                                        new AdminMemberManagementService.Action(
                                                "SUSPEND", "사유", LocalDateTime.now().minusDays(1))))
                .isInstanceOf(BusinessException.class);
        assertThat(m.getSuspendedUntil()).isNull();
        verifyNoInteractions(notifications);
    }

    @Test
    void 닉네임변경요구는_실제닉네임을바꿔야해제된다() {
        var m = target();
        service.act(
                1L,
                2L,
                new AdminMemberManagementService.Action("REQUIRE_NICKNAME", "부적절한 닉네임", null));
        assertThat(m.isNicknameChangeRequired()).isTrue();
        m.updateProfile("회원", null, null, null, null, null, null);
        assertThat(m.isNicknameChangeRequired()).isTrue();
        m.updateProfile("새회원", null, null, null, null, null, null);
        assertThat(m.isNicknameChangeRequired()).isFalse();
    }

    @Test
    void 제재도_서버관리자검사를거친다() {
        doThrow(new BusinessException(com.gongbang.global.error.ErrorCode.NOT_ADMIN))
                .when(access)
                .requireAdmin(3L);
        assertThatThrownBy(
                        () ->
                                service.act(
                                        3L,
                                        2L,
                                        new AdminMemberManagementService.Action(
                                                "WARN", "사유", null)))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(repository, notifications);
    }

    @Test
    void administratorCannotBeSanctioned() {
        Member m = target();
        m.grantAdmin();
        assertThatThrownBy(
                        () ->
                                service.act(
                                        1L,
                                        2L,
                                        new AdminMemberManagementService.Action(
                                                "WARN", "reason", null)))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(notifications);
    }

    @Test
    void permanentSuspensionRemainsUntilExplicitlyReleased() {
        Member m = target();
        service.act(
                1L,
                2L,
                new AdminMemberManagementService.Action("SUSPEND_PERMANENT", "reason", null));
        assertThat(m.isSuspended()).isTrue();
        assertThat(m.getSuspendedUntil().getYear()).isEqualTo(9999);
        service.act(1L, 2L, new AdminMemberManagementService.Action("UNSUSPEND", "resolved", null));
        assertThat(m.isSuspended()).isFalse();
    }

    @Test
    void requiredNicknameIsReservedAgainstReuse() {
        Member m = target();
        service.act(
                1L,
                2L,
                new AdminMemberManagementService.Action("REQUIRE_NICKNAME", "reason", null));
        verify(repository).blockNickname(m.getNickname());
        assertThat(m.isNicknameChangeRequired()).isTrue();
    }
}
