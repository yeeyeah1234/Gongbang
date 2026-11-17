package com.gongbang.member.domain;

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

/**
 * MY-08·09 인증 요청. 실명은 암호화해서 넣고, 결과가 나오면 NULL로 지운다 (AGENTS 9).
 *
 * <p>TODO(확인 필요): 운영자 확인 화면(ADM-01, GET·PATCH /api/admin/verifications)은 기능정의서 행이 아직 없어 만들지 않았다
 * (API 명세서 비고 「기능정의서 행 추가 필요」).
 */
@Entity
@Table(name = "verification_request")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VerificationRequest {

    public enum TargetType {
        CERTIFICATE,
        AWARD
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 15)
    private TargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "real_name", length = 255)
    private String realName;

    @Column(name = "proof_file_id", nullable = false)
    private Long proofFileId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private VerifyStatus status = VerifyStatus.PENDING;

    @Column(name = "reject_reason", length = 200)
    private String rejectReason;

    @Column(name = "reviewer_id")
    private Long reviewerId;

    @Column(name = "requested_at", insertable = false, updatable = false)
    private LocalDateTime requestedAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    public VerificationRequest(
            Long memberId,
            TargetType targetType,
            Long targetId,
            String encryptedRealName,
            Long proofFileId) {
        this.memberId = memberId;
        this.targetType = targetType;
        this.targetId = targetId;
        this.realName = encryptedRealName;
        this.proofFileId = proofFileId;
    }
}
