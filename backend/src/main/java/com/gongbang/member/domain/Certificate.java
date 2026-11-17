package com.gongbang.member.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** MY-08 자격증. 내용을 고치면 인증이 풀린다(⑥). */
@Entity
@Table(name = "certificate")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Certificate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "issuer", nullable = false, length = 100)
    private String issuer;

    @Column(name = "acquired_date", nullable = false)
    private LocalDate acquiredDate;

    @Column(name = "cert_number", length = 50)
    private String certNumber;

    @Column(name = "proof_file_id")
    private Long proofFileId;

    @Enumerated(EnumType.STRING)
    @Column(name = "verify_status", nullable = false, length = 10)
    private VerifyStatus verifyStatus = VerifyStatus.NONE;

    public Certificate(Long memberId) {
        this.memberId = memberId;
    }

    public void update(
            String name,
            String issuer,
            LocalDate acquiredDate,
            String certNumber,
            Long proofFileId) {
        this.name = name;
        this.issuer = issuer;
        this.acquiredDate = acquiredDate;
        this.certNumber = certNumber;
        this.proofFileId = proofFileId;
        this.verifyStatus = VerifyStatus.NONE;
    }

    public void requestVerify(Long proofFileId) {
        this.proofFileId = proofFileId;
        this.verifyStatus = VerifyStatus.PENDING;
    }
}
