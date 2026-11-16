package com.gongbang.contest.dto;

import com.gongbang.contest.domain.Contest;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** MAIN-02 공모전 상세 (API 명세서 GET /api/contests/{contestId}). */
public record ContestDetailResponse(
        Long contestId,
        String title,
        String host,
        String categoryCode,
        String categoryName,
        String thumbnailUrl,
        LocalDate startDate,
        LocalDate deadline,
        int dDay,
        boolean isClosed,
        String description,
        String sourceUrl,
        String homepageUrl,
        int viewCount,
        boolean isBookmarked) {

    /** isBookmarked는 false로 만들고, 로그인 회원이면 service가 {@link #withBookmark}로 채운다 (MAIN-02 ④). */
    public static ContestDetailResponse of(Contest contest, LocalDate today) {
        LocalDate deadline = contest.getEndDate();
        return new ContestDetailResponse(
                contest.getId(),
                contest.getTitle(),
                contest.getHost(),
                contest.getCategory().getCode(),
                contest.getCategory().getName(),
                contest.getThumbnailUrl(),
                contest.getStartDate(),
                deadline,
                (int) ChronoUnit.DAYS.between(today, deadline),
                deadline.isBefore(today),
                contest.getDescription(),
                contest.getSourceUrl(),
                contest.getHomepageUrl(),
                contest.getViewCount(),
                false);
    }

    public ContestDetailResponse withBookmark(boolean bookmarked) {
        return new ContestDetailResponse(
                contestId,
                title,
                host,
                categoryCode,
                categoryName,
                thumbnailUrl,
                startDate,
                deadline,
                dDay,
                isClosed,
                description,
                sourceUrl,
                homepageUrl,
                viewCount,
                bookmarked);
    }
}
