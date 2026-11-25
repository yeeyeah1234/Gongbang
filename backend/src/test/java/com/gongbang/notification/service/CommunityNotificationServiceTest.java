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
        new CommunityNotificationService(settings, notifications).postCreated(1L, 8L, "제목");
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
