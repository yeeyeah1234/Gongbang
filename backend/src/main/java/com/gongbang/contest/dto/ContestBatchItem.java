package com.gongbang.contest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** CRW-01 배치 요청의 공모전 1건. 크롤러 {@code backend_client.send_batch}와 필드명을 맞춘다. */
public record ContestBatchItem(
        @NotBlank String sourceUrl,
        @NotBlank String sourceSite,
        @NotBlank String title,
        String host,
        @NotBlank String categoryCode,
        String thumbnailUrl,
        LocalDate startDate,
        @NotNull LocalDate deadline,
        String description,
        // AGENTS.md 9장: http(s) 주소만 받는다 (javascript: 등 차단). 없으면 null
        @Pattern(regexp = "^https?://.+") @Size(max = 500) String homepageUrl) {}
