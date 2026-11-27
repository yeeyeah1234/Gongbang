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

    /** status: PENDING / ACCEPTED / DECLINED / CANCELED (팀 초대 알림만) */
    public record Invitation(Long invitationId, String status) {}

    public static NotificationResponse of(Notification n, Actor actor, String invitationStatus) {
        boolean actionable =
                (n.getType() == com.gongbang.notification.domain.NotificationType.TEAM_INVITE
                                && "INVITATION".equals(n.getTargetType()))
                        || (n.getType()
                                        == com.gongbang.notification.domain.NotificationType
                                                .ADMIN_INVITE
                                && "ADMIN_INVITATION".equals(n.getTargetType()));
        Invitation invitation =
                actionable && n.getTargetId() != null && invitationStatus != null
                        ? new Invitation(n.getTargetId(), invitationStatus)
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
