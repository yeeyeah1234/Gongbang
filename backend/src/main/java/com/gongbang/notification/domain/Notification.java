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

/** 사이트 알림 (NOTI-01~04). 30일 지나면 지운다(⑥). */
@Entity
@Table(name = "notification")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "receiver_id", nullable = false)
    private Long receiverId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private NotificationType type;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "target_type", length = 20)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "message", nullable = false, length = 200)
    private String message;

    @Column(name = "link_url", nullable = false, length = 300)
    private String linkUrl;

    @Column(name = "group_count", nullable = false)
    private int groupCount = 1;

    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Notification(
            Long receiverId,
            NotificationType type,
            Long actorId,
            String targetType,
            Long targetId,
            String message,
            String linkUrl,
            LocalDateTime now) {
        this.receiverId = receiverId;
        this.type = type;
        this.actorId = actorId;
        this.targetType = targetType;
        this.targetId = targetId;
        this.message = truncate(message, 200);
        this.linkUrl = truncate(linkUrl, 300);
        this.createdAt = now;
    }

    /** NOTI-04 ④: 같은 글 안 읽은 댓글 알림을 「A님 외 N명이…」로 합친다 */
    public void merge(Long actorId, String message, LocalDateTime now) {
        this.groupCount++;
        this.actorId = actorId;
        this.message = truncate(message, 200);
        this.createdAt = now;
    }

    public void markRead(LocalDateTime now) {
        if (!read) {
            read = true;
            readAt = now;
        }
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
