package com.gongbang.notification.service;

import com.gongbang.notification.domain.NotificationType;
import com.gongbang.notification.domain.SettingType;
import com.gongbang.notification.repository.NotificationSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CommunityNotificationService {
    private final NotificationSettingRepository settings;
    private final NotificationService notifications;

    @Transactional
    public void postCreated(Long authorId, Long postId, String title) {
        for (var setting :
                settings.findBySettingTypeAndSiteEnabledTrue(SettingType.COMMUNITY_NEW)) {
            if (authorId.equals(setting.getMemberId())) continue;
            notifications.send(
                    setting.getMemberId(),
                    NotificationType.BOARD_POST_NEW,
                    authorId,
                    "BOARD_POST",
                    postId,
                    "커뮤니티에 새 글이 올라왔어요: " + title,
                    "/board/" + postId);
        }
    }
}
