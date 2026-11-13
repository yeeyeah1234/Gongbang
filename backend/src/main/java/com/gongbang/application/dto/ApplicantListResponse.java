package com.gongbang.application.dto;

import com.gongbang.application.domain.Application;
import java.time.LocalDateTime;
import java.util.List;

/** REC-003 지원자 목록 (API 명세서 GET /api/posts/{postId}/applications). */
public record ApplicantListResponse(Counts counts, List<Item> items) {

    /** 탭마다 인원 수 (②). */
    public record Counts(long pending, long accepted, long rejected) {}

    /** 지원자 한 명 (③). createdAt은 지원 일시(화면 표기 MM.DD HH:mm). */
    public record Item(Long applicationId, MemberInfo member, LocalDateTime createdAt) {

        public static Item of(Application application, MemberInfo member) {
            return new Item(application.getId(), member, application.getCreatedAt());
        }
    }

    /** 지원자 프로필 (INT-002 ①). 탈퇴 회원은 nickname null → 화면 「탈퇴한 사용자」. */
    public record MemberInfo(
            Long memberId, String nickname, String profileImageUrl, String major, String bio) {}
}
