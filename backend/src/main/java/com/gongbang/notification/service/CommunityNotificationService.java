package com.gongbang.notification.service;

import com.gongbang.notification.domain.NotificationType;
import com.gongbang.notification.domain.SettingType;
import com.gongbang.notification.repository.NotificationSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommunityNotificationService {
    private final NotificationSettingRepository settings;
    private final NotificationService notifications;
    private final ApplicationEventPublisher events;

    public void postCreated(Long authorId, Long postId, String title) {
        events.publishEvent(new CommunityPostCreatedEvent(authorId, postId, title));
    }

    // 비동기 작업에서 호출한다. 수신자별 NotificationService.send의 트랜잭션을 사용한다.
    public void deliver(CommunityPostCreatedEvent event) {
        for (var setting :
                settings.findBySettingTypeAndSiteEnabledTrue(SettingType.COMMUNITY_NEW)) {
            if (event.authorId().equals(setting.getMemberId())) continue;
            try {
                notifications.send(
                        setting.getMemberId(),
                        NotificationType.BOARD_POST_NEW,
                        event.authorId(),
                        "BOARD_POST",
                        event.postId(),
                        "커뮤니티에 새 글이 올라왔어요: " + event.title(),
                        "/board/" + event.postId());
            } catch (RuntimeException failure) {
                log.error(
                        "Community notification failed: postId={}, receiverId={}, errorType={}",
                        event.postId(),
                        setting.getMemberId(),
                        failure.getClass().getSimpleName());
            }
        }
    }
}
