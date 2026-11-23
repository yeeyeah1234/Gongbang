package com.gongbang.notification.service;

import com.gongbang.workspace.notice.event.NoticeCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NoticeCreatedListener {

    private final NoticeNotificationService noticeNotificationService;

    /** 발행자가 이미 AFTER_COMMIT에서 발행하므로 커밋 대기를 다시 등록하지 않는다. */
    @EventListener
    public void handle(NoticeCreatedEvent event) {
        try {
            noticeNotificationService.send(event);
        } catch (RuntimeException e) {
            // 공지는 이미 저장됐다. 알림 실패를 공지 등록 실패로 응답하지 않는다.
            log.warn("공지 알림 발송 실패: teamId={}, noticeId={}", event.teamId(), event.noticeId(), e);
        }
    }
}
