package com.gongbang.contest.dto;

import com.gongbang.contest.domain.Contest;
import java.time.LocalDate;

/** 다른 도메인(모집글 등)에 넘기는 공모전 요약. thumbnailUrl은 없거나 오류면 null (화면은 기본 이미지). */
public record ContestSummary(
        Long contestId,
        String title,
        String categoryName,
        LocalDate deadline,
        String thumbnailUrl) {

    public static ContestSummary from(Contest contest) {
        return new ContestSummary(
                contest.getId(),
                contest.getTitle(),
                contest.getCategory().getName(),
                contest.getEndDate(),
                contest.getThumbnailUrl());
    }
}
