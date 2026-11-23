package com.gongbang.notification.service;

import com.gongbang.notification.domain.NotificationType;
import com.gongbang.team.service.TeamMemberService;
import com.gongbang.workspace.notice.event.NoticeCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NoticeNotificationService {

    private final TeamMemberService teamMemberService;
    private final NotificationService notificationService;

    /** 커밋 후 호출되므로 기존 트랜잭션을 재사용하지 않는다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void send(NoticeCreatedEvent event) {
        for (Long receiverId : teamMemberService.findActiveNonLeaderMemberIds(event.teamId())) {
            // 수신 설정, 탈퇴 회원, 작성자 본인 제외는 공통 send에서 처리한다.
            notificationService.send(
                    receiverId,
                    NotificationType.NOTICE_NEW,
                    event.memberId(),
                    "NOTICE",
                    event.noticeId(),
                    "새 공지: " + event.title(),
                    event.linkUrl());
        }
    }
}
