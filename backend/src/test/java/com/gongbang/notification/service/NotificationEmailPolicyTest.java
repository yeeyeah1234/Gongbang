package com.gongbang.notification.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gongbang.member.domain.Member;
import com.gongbang.member.service.MemberService;
import com.gongbang.notification.domain.*;
import com.gongbang.notification.repository.*;
import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class NotificationEmailPolicyTest {
    final NotificationRepository notifications = mock(NotificationRepository.class);
    final NotificationSettingRepository settings = mock(NotificationSettingRepository.class);
    final MemberService members = mock(MemberService.class);
    final EmailOutboxService emails = mock(EmailOutboxService.class);
    final NotificationService service =
            new NotificationService(notifications, settings, members, emails);

    @ParameterizedTest
    @EnumSource(
            value = NotificationType.class,
            names = {"TEAM_DEADLINE", "POST_DELETED", "VERIFY_RESULT"})
    void 마감_삭제_인증결과는_이메일_대기열에_들어간다(NotificationType type) {
        var receiver = Member.createEmailMember("member@example.com", "hash", "회원");
        when(members.findActiveMember(2L)).thenReturn(Optional.of(receiver));
        service.send(2L, type, null, "TEST", 1L, "내용", "/notifications");
        verify(emails)
                .enqueueNotification(
                        eq(receiver), eq(type), isNull(), eq("내용"), eq("/notifications"), any());
        org.assertj.core.api.Assertions.assertThat(type.getSendMode())
                .isEqualTo(SendMode.IMMEDIATE);
    }

    @ParameterizedTest
    @EnumSource(
            value = NotificationType.class,
            names = {"TEAM_DEADLINE", "POST_DELETED"})
    void 이메일을_끈_회원에게는_메일을_보내지않는다(NotificationType type) {
        var receiver = Member.createEmailMember("member@example.com", "hash", "회원");
        when(members.findActiveMember(2L)).thenReturn(Optional.of(receiver));
        var setting = new NotificationSetting(2L, type.getSettingType());
        setting.change(true, false);
        when(settings.findById(any())).thenReturn(Optional.of(setting));
        service.send(2L, type, null, "TEST", 1L, "내용", "/notifications");
        verifyNoInteractions(emails);
    }
}
