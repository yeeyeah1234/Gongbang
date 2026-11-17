package com.gongbang.post.dto;

import java.time.LocalDate;

/**
 * PST-002 모집글 작성 요청 (API 명세서 POST /api/posts). contestId와 external 중 하나만 보낸다.
 *
 * <p>값 검증은 PostCommandService가 한다 (에러 코드 CONTEST_REQUIRED·INVALID_PERIOD를 나눠 보내야 해서).
 */
public record PostCreateRequest(
        Long contestId,
        External external,
        String title,
        Integer targetCount,
        LocalDate recruitStartDate,
        LocalDate recruitEndDate,
        String relatedUrl,
        String content) {

    /** 외부 공모전 직접 입력 (PST-002 ②). */
    public record External(String title, String categoryCode, LocalDate deadline, String url) {}
}
