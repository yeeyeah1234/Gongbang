package com.gongbang.member.dto;

import com.gongbang.member.domain.DesiredRole;
import com.gongbang.member.domain.Member;
import java.time.LocalDateTime;
import java.util.List;

/** API 명세서 MY-01 GET /api/members/me (GNB도 이 API) */
public record MyProfileResponse(
        Long memberId,
        String email,
        String loginType,
        String role,
        String nickname,
        String profileImageUrl,
        String bio,
        String school,
        String major,
        String grade,
        List<String> interestCategoryCodes,
        List<String> desiredRoles,
        List<String> skillTags,
        String portfolioUrl,
        int profileCompletion,
        LocalDateTime createdAt) {

    public static MyProfileResponse of(Member m, List<String> interestCodes, int completion) {
        return new MyProfileResponse(
                m.getId(),
                m.getEmail(),
                m.getLoginType().name(),
                m.getRole().name(),
                m.getNickname(),
                m.getProfileImageUrl(),
                m.getBio(),
                m.getSchool(),
                m.getMajor(),
                m.getGrade() == null ? null : m.getGrade().name(),
                interestCodes,
                m.getDesiredRoles().stream().map(DesiredRole::name).toList(),
                List.copyOf(m.getSkillTags()),
                m.getPortfolioUrl(),
                completion,
                m.getCreatedAt());
    }
}
