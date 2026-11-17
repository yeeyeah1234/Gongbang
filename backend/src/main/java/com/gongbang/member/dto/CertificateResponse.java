package com.gongbang.member.dto;

import com.gongbang.member.domain.Certificate;
import java.time.LocalDate;

/** 본인 화면용 (증빙 파일 id 포함 — 본인만 받는다) */
public record CertificateResponse(
        Long certificateId,
        String name,
        String issuer,
        LocalDate acquiredDate,
        String certNumber,
        Long proofFileId,
        String verifyStatus) {

    public static CertificateResponse from(Certificate c) {
        return new CertificateResponse(
                c.getId(),
                c.getName(),
                c.getIssuer(),
                c.getAcquiredDate(),
                c.getCertNumber(),
                c.getProofFileId(),
                c.getVerifyStatus().name());
    }
}
