package com.gongbang.application.domain;

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

/** 모집글 지원. 한 회원은 한 글에 한 번만 지원한다 (UQ post_id, applicant_id). */
@Entity
@Table(name = "application")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 다른 도메인(모집글)과는 ID로만 잇는다
    @Column(name = "post_id", nullable = false, updatable = false)
    private Long postId;

    // TODO(확인 필요): member 엔티티(정진우)가 생기면 연관관계로 바꿀지 정한다. 지금은 ID만 둔다.
    @Column(name = "applicant_id", nullable = false, updatable = false)
    private Long applicantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private ApplicationStatus status = ApplicationStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "reject_type", length = 10)
    private RejectType rejectType;

    // DB가 채우는 지원 시각. 지원자 목록은 먼저 지원한 순 (REC-003 ④).
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 수락·거절·종료 시각. */
    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    private Application(Long postId, Long applicantId) {
        this.postId = postId;
        this.applicantId = applicantId;
    }

    /** REC-001 ②: 「대기」 상태 지원을 만든다. */
    public static Application pending(Long postId, Long applicantId) {
        return new Application(postId, applicantId);
    }
}
