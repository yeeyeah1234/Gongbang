package com.gongbang.post.dto;

import com.gongbang.contest.dto.ContestSummary;
import com.gongbang.post.domain.ExternalContest;
import com.gongbang.post.domain.MyStatus;
import com.gongbang.post.domain.PostStatus;
import com.gongbang.post.domain.RecruitPost;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/** PST-005 모집글 상세 (API 명세서 GET /api/posts/{postId}). */
public record PostDetailResponse(
        Long postId,
        String title,
        String content,
        ContestInfo contest,
        String imageUrl,
        AuthorInfo author,
        LocalDateTime createdAt,
        int viewCount,
        int currentCount,
        int targetCount,
        PostStatus status,
        LocalDate recruitStartDate,
        LocalDate recruitEndDate,
        String relatedUrl,
        MyStatus myStatus,
        Long myApplicationId) {

    /**
     * 공모전 카드 (①). dDay는 마감 당일 0, 지나면 음수. url은 외부 공모전 링크(새 탭)이고 서비스 공모전이면 null(화면이 contestId로 공모전
     * 상세를 연다).
     */
    public record ContestInfo(
            Long contestId,
            String title,
            String categoryName,
            boolean isExternal,
            LocalDate deadline,
            int dDay,
            String url) {

        static ContestInfo of(ContestSummary contest, LocalDate today) {
            return new ContestInfo(
                    contest.contestId(),
                    contest.title(),
                    contest.categoryName(),
                    false,
                    contest.deadline(),
                    dDay(today, contest.deadline()),
                    null);
        }

        static ContestInfo of(ExternalContest external, String categoryName, LocalDate today) {
            return new ContestInfo(
                    null,
                    external.getTitle(),
                    categoryName,
                    true,
                    external.getDeadline(),
                    dDay(today, external.getDeadline()),
                    external.getUrl());
        }

        private static int dDay(LocalDate today, LocalDate deadline) {
            return (int) ChronoUnit.DAYS.between(today, deadline);
        }
    }

    public record AuthorInfo(Long memberId, String nickname, String profileImageUrl) {}

    /** 서비스 공모전 글. 대표 이미지는 공모전 썸네일 (⑤). */
    public static PostDetailResponse of(
            RecruitPost post,
            ContestSummary contest,
            AuthorInfo author,
            int viewCount,
            MyStatus myStatus,
            Long myApplicationId,
            LocalDate today) {
        return of(
                post,
                ContestInfo.of(contest, today),
                contest.thumbnailUrl(),
                author,
                viewCount,
                myStatus,
                myApplicationId);
    }

    /** 외부 공모전 글. 대표 이미지는 링크에서 불러온 og:image (⑤). */
    public static PostDetailResponse of(
            RecruitPost post,
            String categoryName,
            AuthorInfo author,
            int viewCount,
            MyStatus myStatus,
            Long myApplicationId,
            LocalDate today) {
        ExternalContest external = post.getExternalContest();
        return of(
                post,
                ContestInfo.of(external, categoryName, today),
                external.getImageUrl(),
                author,
                viewCount,
                myStatus,
                myApplicationId);
    }

    private static PostDetailResponse of(
            RecruitPost post,
            ContestInfo contest,
            String imageUrl,
            AuthorInfo author,
            int viewCount,
            MyStatus myStatus,
            Long myApplicationId) {
        return new PostDetailResponse(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                contest,
                imageUrl,
                author,
                post.getCreatedAt(),
                viewCount,
                post.getCurrentCount(),
                post.getTargetCount(),
                post.getStatus(),
                post.getRecruitStartDate(),
                post.getRecruitEndDate(),
                post.getRelatedUrl(),
                myStatus,
                myApplicationId);
    }
}
