package com.gongbang.member.service;

import com.gongbang.member.domain.Member;

/**
 * AUTH-07 ③, MY-01 ⑥ 프로필 완성도(%).
 *
 * <p>TODO(확인 필요): 항목별 점수는 기능정의서에 없어 정함 — 사진·한 줄 소개·학교·전공·학년·관심 분야·희망 역할·기술 태그·포트폴리오·경력·자격증·수상 12개를
 * 똑같이 나눈다. (Figma 08-1은 「자격증을 추가하면 85%」)
 */
public final class ProfileCompletion {

    private ProfileCompletion() {}

    public static int calculate(
            Member m, long careerCount, long certificateCount, long awardCount) {
        boolean[] filled = {
            m.getProfileImageUrl() != null,
            notBlank(m.getBio()),
            notBlank(m.getSchool()),
            notBlank(m.getMajor()),
            m.getGrade() != null,
            !m.getInterestCategoryIds().isEmpty(),
            !m.getDesiredRoles().isEmpty(),
            !m.getSkillTags().isEmpty(),
            notBlank(m.getPortfolioUrl()),
            careerCount > 0,
            certificateCount > 0,
            awardCount > 0,
        };
        int count = 0;
        for (boolean f : filled) {
            if (f) {
                count++;
            }
        }
        return Math.round(count * 100f / filled.length);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
