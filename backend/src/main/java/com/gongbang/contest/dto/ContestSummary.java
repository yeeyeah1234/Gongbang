package com.gongbang.contest.dto;

import com.gongbang.contest.domain.Contest;
import java.time.LocalDate;

/** 다른 도메인(모집글 등)에 넘기는 공모전 요약. */
public record ContestSummary(
        Long contestId, String title, String categoryName, LocalDate deadline) {

    public static ContestSummary from(Contest contest) {
        return new ContestSummary(
                contest.getId(),
                contest.getTitle(),
                contest.getCategory().getName(),
                contest.getEndDate());
    }
}
