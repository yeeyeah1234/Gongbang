package com.gongbang.report.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 신고 접수 엔티티 (`report`). */
@Entity
@Table(name = "report")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reporter_id", nullable = false)
    private Long reporterId;

    @Column(name = "reported_member_id", nullable = false)
    private Long reportedMemberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 10)
    private ReportTargetType targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", nullable = false, length = 20)
    private ReportReasonCode reasonCode;

    @Column(name = "detail", length = 500)
    private String detail;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private ReportStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private Report(
            Long reporterId,
            Long reportedMemberId,
            ReportTargetType targetType,
            Long targetId,
            ReportReasonCode reasonCode,
            String detail) {
        this.reporterId = reporterId;
        this.reportedMemberId = reportedMemberId;
        this.targetType = targetType;
        this.targetId = targetId;
        this.reasonCode = reasonCode;
        this.detail = detail;
        this.status = ReportStatus.RECEIVED;
    }

    /** 새 신고 엔티티를 생성한다 (초기 상태 `RECEIVED`). */
    public static Report create(
            Long reporterId,
            Long reportedMemberId,
            ReportTargetType targetType,
            Long targetId,
            ReportReasonCode reasonCode,
            String detail) {
        return new Report(reporterId, reportedMemberId, targetType, targetId, reasonCode, detail);
    }

    /** 신고 처리 상태를 변경한다 (ADM-03용). */
    public void updateStatus(ReportStatus status) {
        if (status != null) {
            this.status = status;
        }
    }

    @PrePersist
    protected void onCreate() {
        if (this.status == null) {
            this.status = ReportStatus.RECEIVED;
        }
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
