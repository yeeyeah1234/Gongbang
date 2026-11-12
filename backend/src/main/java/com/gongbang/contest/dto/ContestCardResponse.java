package com.gongbang.contest.dto;

import com.gongbang.contest.domain.Contest;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** 공모전 카드 (MAIN-06). 검색 결과·북마크 목록도 같은 모양을 쓴다. */
public record ContestCardResponse(
        Long contestId,
        String title,
        String host,
        String categoryCode,
        String categoryName,
        String thumbnailUrl,
        LocalDate deadline,
        int dDay,
        boolean isClosed,
        boolean isBookmarked) {

    /** dDay는 마감 당일 0, 마감이 지나면 음수. isClosed는 마감일이 오늘보다 앞일 때 true. */
    public static ContestCardResponse of(Contest contest, LocalDate today) {
        LocalDate deadline = contest.getEndDate();
        return new ContestCardResponse(
                contest.getId(),
                contest.getTitle(),
                contest.getHost(),
                contest.getCategory().getCode(),
                contest.getCategory().getName(),
                contest.getThumbnailUrl(),
                deadline,
                (int) ChronoUnit.DAYS.between(today, deadline),
                deadline.isBefore(today),
                // TODO(확인 필요): 북마크(MY-03, 정진우)·로그인 사용자 조회가 생기면 실제 값으로 바꾼다
                false);
    }
}
