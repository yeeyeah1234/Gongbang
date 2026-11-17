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

/** AUTH-04 약관 동의 기록. 마케팅(선택) 미동의도 남긴다. */
@Entity
@Table(name = "terms_agreement")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TermsAgreement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "terms_type", nullable = false, length = 20)
    private TermsType termsType;

    @Column(name = "terms_version", nullable = false, length = 10)
    private String termsVersion;

    @Column(name = "agreed", nullable = false)
    private boolean agreed;

    @Column(name = "agreed_at", insertable = false, updatable = false)
    private LocalDateTime agreedAt;

    public TermsAgreement(Long memberId, TermsType termsType, String termsVersion, boolean agreed) {
        this.memberId = memberId;
        this.termsType = termsType;
        this.termsVersion = termsVersion;
        this.agreed = agreed;
    }
}
