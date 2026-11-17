package com.gongbang.post.service;

import com.gongbang.notification.domain.NotificationType;
import com.gongbang.notification.service.NotificationService;
import com.gongbang.post.domain.RecruitPost;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** INT-003 모집 알림. 발생 조건별 문구와 이동 주소를 한 곳에 둔다 (알림 화면·수신 설정은 NotificationService가 처리). */
@Component
@RequiredArgsConstructor
public class RecruitNotifier {

    static final String TARGET_TYPE = "POST";

    private final NotificationService notificationService;

    /** INT-003 ②: 수락 → 지원자. 누르면 모집글 상세. */
    public void accepted(RecruitPost post, Long applicantId, Long actorId) {
        send(applicantId, NotificationType.APPLY_ACCEPTED, actorId, post, "‘%s’ 팀에 합류했어요");
    }

    /** INT-003 ③: 거절(자동 거절 포함) → 지원자. 누르면 모집글 상세. 자동 거절은 actorId null(시스템). */
    public void rejected(RecruitPost post, Long applicantId, Long actorId) {
        send(applicantId, NotificationType.APPLY_REJECTED, actorId, post, "‘%s’ 지원이 거절되었어요");
    }

    private void send(
            Long receiverId, NotificationType type, Long actorId, RecruitPost post, String format) {
        notificationService.send(
                receiverId,
                type,
                actorId,
                TARGET_TYPE,
                post.getId(),
                String.format(format, post.getTitle()),
                detailLink(post.getId()));
    }

    /** 모집글 상세 화면 주소 (프론트 경로 /posts/{postId}). */
    static String detailLink(Long postId) {
        return "/posts/" + postId;
    }
}
