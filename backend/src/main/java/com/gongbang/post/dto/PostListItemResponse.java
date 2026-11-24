package com.gongbang.post.dto;

import com.gongbang.contest.dto.ContestSummary;
import com.gongbang.post.domain.ExternalContest;
import com.gongbang.post.domain.PostStatus;
import com.gongbang.post.domain.RecruitPost;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** PST-001 모집글 목록 항목 (API 명세서 GET /api/posts). */
public record PostListItemResponse(
        Long postId,
        String title,
        ContestInfo contest,
        AuthorInfo author,
        LocalDateTime createdAt,
        int viewCount,
        int currentCount,
        int targetCount,
        PostStatus status,
        // ④ 모집중인데 이 날짜가 지났으면 화면이 「모집마감」으로 보여 준다 (상태값은 그대로)
        LocalDate recruitEndDate) {

    /** 공모전 배지·제목. 외부 공모전이면 contestId는 null, isExternal은 true (화면 「외부」 표시). */
    public record ContestInfo(
            Long contestId,
            String title,
            String categoryName,
            boolean isExternal,
            LocalDate deadline) {

        public static ContestInfo of(ContestSummary contest) {
            return new ContestInfo(
                    contest.contestId(),
                    contest.title(),
                    contest.categoryName(),
                    false,
                    contest.deadline());
        }

        public static ContestInfo of(ExternalContest external, String categoryName) {
            return new ContestInfo(
                    null, external.getTitle(), categoryName, true, external.getDeadline());
        }
    }

    public record AuthorInfo(Long memberId, String nickname) {}

    public static PostListItemResponse of(
            RecruitPost post, ContestInfo contest, AuthorInfo author) {
        return new PostListItemResponse(
                post.getId(),
                post.getTitle(),
                contest,
                author,
                post.getCreatedAt(),
                post.getViewCount(),
                post.getCurrentCount(),
                post.getTargetCount(),
                post.getStatus(),
                post.getRecruitEndDate());
    }
}
