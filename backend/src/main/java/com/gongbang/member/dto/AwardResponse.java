package com.gongbang.member.dto;

import com.gongbang.member.domain.Award;
import java.time.LocalDate;

public record AwardResponse(
        Long awardId,
        String contestName,
        String awardName,
        LocalDate awardedDate,
        Long proofFileId,
        String verifyStatus) {

    public static AwardResponse from(Award a) {
        return new AwardResponse(
                a.getId(),
                a.getContestName(),
                a.getAwardName(),
                a.getAwardedDate(),
                a.getProofFileId(),
                a.getVerifyStatus().name());
    }
}
