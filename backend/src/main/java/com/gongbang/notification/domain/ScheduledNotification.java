package com.gongbang.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** NOTI-05 정해진 시각 알림. 일정이 바뀌면 기존 행을 CANCELED로 바꾸고 새로 넣는다. */
@Entity
@Table(name = "scheduled_notification")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScheduledNotification {

    public enum Status {
        PENDING,
        SENT,
        CANCELED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private NotificationType type;

    @Column(name = "target_type", nullable = false, length = 20)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "fire_at", nullable = false)
    private LocalDateTime fireAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private Status status = Status.PENDING;

    public ScheduledNotification(
            NotificationType type, String targetType, Long targetId, LocalDateTime fireAt) {
        this.type = type;
        this.targetType = targetType;
        this.targetId = targetId;
        this.fireAt = fireAt;
    }

    public void cancel() {
        status = Status.CANCELED;
    }

    public void sent() {
        status = Status.SENT;
    }
}
