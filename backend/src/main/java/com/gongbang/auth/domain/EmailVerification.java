package com.gongbang.auth.domain;

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

/** AUTH-05 인증번호. 원문은 저장하지 않고 해시만 둔다. */
@Entity
@Table(name = "email_verification")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailVerification {

    /** AUTH-05 ③: 5번 틀리면 그 번호는 무효 */
    public static final int MAX_FAIL = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", nullable = false, length = 100)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 10)
    private VerificationPurpose purpose;

    @Column(name = "code_hash", nullable = false, length = 100)
    private String codeHash;

    @Column(name = "fail_count", nullable = false)
    private int failCount = 0;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public EmailVerification(
            String email,
            VerificationPurpose purpose,
            String codeHash,
            LocalDateTime now,
            int validMinutes) {
        this.email = email;
        this.purpose = purpose;
        this.codeHash = codeHash;
        this.createdAt = now;
        this.expiresAt = now.plusMinutes(validMinutes);
    }

    public boolean isLocked() {
        return failCount >= MAX_FAIL;
    }

    public boolean isExpired(LocalDateTime now) {
        return !expiresAt.isAfter(now);
    }

    public int fail() {
        failCount++;
        return MAX_FAIL - failCount;
    }

    public void verify(LocalDateTime now) {
        verifiedAt = now;
    }
}
