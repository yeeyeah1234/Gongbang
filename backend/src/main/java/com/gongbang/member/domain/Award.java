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

/** MY-09 수상 경력. 인증 절차는 자격증과 같다. */
@Entity
@Table(name = "award")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Award {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "contest_name", nullable = false, length = 100)
    private String contestName;

    @Column(name = "award_name", nullable = false, length = 50)
    private String awardName;

    @Column(name = "awarded_date", nullable = false)
    private LocalDate awardedDate;

    @Column(name = "proof_file_id")
    private Long proofFileId;

    @Enumerated(EnumType.STRING)
    @Column(name = "verify_status", nullable = false, length = 10)
    private VerifyStatus verifyStatus = VerifyStatus.NONE;

    public Award(Long memberId) {
        this.memberId = memberId;
    }

    public void update(
            String contestName, String awardName, LocalDate awardedDate, Long proofFileId) {
        this.contestName = contestName;
        this.awardName = awardName;
        this.awardedDate = awardedDate;
        this.proofFileId = proofFileId;
        this.verifyStatus = VerifyStatus.NONE;
    }

    public void requestVerify(Long proofFileId) {
        this.proofFileId = proofFileId;
        this.verifyStatus = VerifyStatus.PENDING;
    }

    public void review(VerifyStatus status) {
        this.verifyStatus = status;
    }
}
