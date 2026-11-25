package com.gongbang.notification.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gongbang.notification.domain.*;
import com.gongbang.notification.repository.NotificationSettingRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class CommunityNotificationServiceTest {
    @Test
    void 신청회원에게만_보내고_작성자는_제외한다() {
        var settings = mock(NotificationSettingRepository.class);
        var notifications = mock(NotificationService.class);
        when(settings.findBySettingTypeAndSiteEnabledTrue(SettingType.COMMUNITY_NEW))
                .thenReturn(
                        List.of(
                                new NotificationSetting(1L, SettingType.COMMUNITY_NEW),
                                new NotificationSetting(2L, SettingType.COMMUNITY_NEW)));
        new CommunityNotificationService(
                        settings,
                        notifications,
                        mock(org.springframework.context.ApplicationEventPublisher.class))
                .deliver(new CommunityPostCreatedEvent(1L, 8L, "제목"));
        verify(notifications)
                .send(
                        2L,
                        NotificationType.BOARD_POST_NEW,
                        1L,
                        "BOARD_POST",
                        8L,
                        "커뮤니티에 새 글이 올라왔어요: 제목",
                        "/board/8");
        verifyNoMoreInteractions(notifications);
    }

    @Test
    void 글생성에서는_이벤트만발행하고_수신자실패는_다음수신자를_막지않는다() {
        var settings = mock(NotificationSettingRepository.class);
        var notifications = mock(NotificationService.class);
        var events = mock(org.springframework.context.ApplicationEventPublisher.class);
        var service = new CommunityNotificationService(settings, notifications, events);
        var event = new CommunityPostCreatedEvent(1L, 8L, "제목");
        service.postCreated(1L, 8L, "제목");
        verify(events).publishEvent(event);
        verifyNoInteractions(settings, notifications);
        when(settings.findBySettingTypeAndSiteEnabledTrue(SettingType.COMMUNITY_NEW))
                .thenReturn(
                        List.of(
                                new NotificationSetting(2L, SettingType.COMMUNITY_NEW),
                                new NotificationSetting(3L, SettingType.COMMUNITY_NEW)));
        doThrow(new IllegalStateException("failure"))
                .when(notifications)
                .send(eq(2L), any(), any(), any(), any(), any(), any());
        assertThatCode(() -> service.deliver(event)).doesNotThrowAnyException();
        verify(notifications)
                .send(
                        eq(3L),
                        eq(NotificationType.BOARD_POST_NEW),
                        eq(1L),
                        eq("BOARD_POST"),
                        eq(8L),
                        anyString(),
                        eq("/board/8"));
    }

    @Test
    void 커뮤니티는_기본꺼짐이며_이메일은_켜지지않는다() {
        var setting = new NotificationSetting(1L, SettingType.COMMUNITY_NEW);
        assertThat(setting.isSiteEnabled()).isFalse();
        assertThat(setting.isEmailEnabled()).isFalse();
        setting.change(true, true);
        assertThat(setting.isSiteEnabled()).isTrue();
        assertThat(setting.isEmailEnabled()).isFalse();
        assertThat(NotificationType.BOARD_POST_NEW.getSendMode()).isNull();
    }
}
