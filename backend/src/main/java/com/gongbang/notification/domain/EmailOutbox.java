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

/** NOTI-06 메일 대기열. 바로 SMTP를 부르지 않고 여기에 넣으면 1분마다 보낸다. */
@Entity
@Table(name = "email_outbox")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailOutbox {

    public enum Status {
        PENDING,
        SENT,
        FAILED,
        CANCELED
    }

    public static final int MAX_RETRY = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "receiver_id")
    private Long receiverId;

    @Column(name = "to_email", nullable = false, length = 100)
    private String toEmail;

    @Column(name = "mail_type", nullable = false, length = 30)
    private String mailType;

    @Column(name = "is_security", nullable = false)
    private boolean security;

    @Enumerated(EnumType.STRING)
    @Column(name = "send_mode", nullable = false, length = 20)
    private SendMode sendMode = SendMode.IMMEDIATE;

    @Column(name = "notification_id")
    private Long notificationId;

    @Column(name = "subject", nullable = false, length = 200)
    private String subject;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private Status status = Status.PENDING;

    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    public EmailOutbox(
            Long receiverId,
            String toEmail,
            String mailType,
            boolean security,
            SendMode sendMode,
            Long notificationId,
            String subject,
            String body,
            LocalDateTime scheduledAt) {
        this.receiverId = receiverId;
        this.toEmail = toEmail;
        this.mailType = mailType;
        this.security = security;
        this.sendMode = sendMode;
        this.notificationId = notificationId;
        this.subject = subject;
        this.body = body;
        this.scheduledAt = scheduledAt;
    }

    public void sent(LocalDateTime now) {
        status = Status.SENT;
        sentAt = now;
    }

    public void failed() {
        retryCount++;
        if (retryCount >= MAX_RETRY) {
            status = Status.FAILED;
        }
    }

    public void cancel() {
        status = Status.CANCELED;
    }

    public void postpone(LocalDateTime to) {
        scheduledAt = to;
    }
}
