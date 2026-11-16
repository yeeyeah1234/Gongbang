package com.gongbang.member.dto;

import com.gongbang.member.domain.Award;
import com.gongbang.member.domain.Career;
import com.gongbang.member.domain.Certificate;
import com.gongbang.member.domain.DesiredRole;
import com.gongbang.member.domain.Member;
import com.gongbang.member.domain.VerifyStatus;
import java.time.LocalDate;
import java.util.List;

/** API 명세서 MY-05. 이메일·가입 방식·실명·증빙 파일은 넣지 않는다. */
public record PublicProfileResponse(
        Long memberId,
        String nickname,
        String profileImageUrl,
        String bio,
        String school,
        String major,
        List<String> interestCategoryCodes,
        List<String> desiredRoles,
        List<String> skillTags,
        String portfolioUrl,
        List<PublicCareer> careers,
        List<PublicCertificate> certificates,
        List<PublicAward> awards) {

    public record PublicCareer(
            String organization,
            String role,
            LocalDate startDate,
            LocalDate endDate,
            String linkUrl) {}

    public record PublicCertificate(
            String name, String issuer, LocalDate acquiredDate, boolean verified) {}

    public record PublicAward(
            String contestName, String awardName, LocalDate awardedDate, boolean verified) {}

    public static PublicProfileResponse of(
            Member m,
            List<String> interestCodes,
            List<Career> careers,
            List<Certificate> certificates,
            List<Award> awards) {
        return new PublicProfileResponse(
                m.getId(),
                m.getNickname(),
                m.getProfileImageUrl(),
                m.getBio(),
                m.getSchool(),
                m.getMajor(),
                interestCodes,
                m.getDesiredRoles().stream().map(DesiredRole::name).toList(),
                List.copyOf(m.getSkillTags()),
                m.getPortfolioUrl(),
                careers.stream()
                        .map(
                                c ->
                                        new PublicCareer(
                                                c.getOrganization(),
                                                c.getRole(),
                                                c.getStartDate(),
                                                c.getEndDate(),
                                                c.getLinkUrl()))
                        .toList(),
                certificates.stream()
                        .map(
                                c ->
                                        new PublicCertificate(
                                                c.getName(),
                                                c.getIssuer(),
                                                c.getAcquiredDate(),
                                                c.getVerifyStatus() == VerifyStatus.VERIFIED))
                        .toList(),
                awards.stream()
                        .map(
                                a ->
                                        new PublicAward(
                                                a.getContestName(),
                                                a.getAwardName(),
                                                a.getAwardedDate(),
                                                a.getVerifyStatus() == VerifyStatus.VERIFIED))
                        .toList());
    }
}
