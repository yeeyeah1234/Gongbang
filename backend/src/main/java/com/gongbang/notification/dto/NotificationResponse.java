package com.gongbang.notification.dto;

import com.gongbang.notification.domain.Notification;
import java.time.LocalDateTime;

/** API 명세서 NOTI-01·02 */
public record NotificationResponse(
        Long notificationId,
        String type,
        String category,
        String message,
        String linkUrl,
        Actor actor,
        boolean isRead,
        int groupCount,
        LocalDateTime createdAt,
        Invitation invitation) {

    public record Actor(Long memberId, String nickname) {}

    /** status: PENDING / ACCEPTED / REJECTED / CLOSED (팀 초대 알림만) */
    public record Invitation(Long invitationId, String status) {}

    public static NotificationResponse of(Notification n, Actor actor) {
        Invitation invitation =
                "INVITATION".equals(n.getTargetType()) && n.getTargetId() != null
                        // TODO(확인 필요): 초대 상태는 team_invitation이 생기면 실제 값으로
                        ? new Invitation(n.getTargetId(), "PENDING")
                        : null;
        return new NotificationResponse(
                n.getId(),
                n.getType().name(),
                n.getType().getCategory().name(),
                n.getMessage(),
                n.getLinkUrl(),
                actor,
                n.isRead(),
                n.getGroupCount(),
                n.getCreatedAt(),
                invitation);
    }
}
