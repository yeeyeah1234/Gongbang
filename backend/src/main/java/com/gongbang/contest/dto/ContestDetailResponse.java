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
        int viewCount,
        boolean isBookmarked) {

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
                contest.getViewCount(),
                // TODO(확인 필요): 북마크(MY-03, 정진우)·로그인 사용자 조회가 생기면 실제 값으로 바꾼다
                false);
    }
}
