package com.gongbang.contest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
        // 주소 형식은 저장할 때 공통 UrlPolicy로 검사·정규화한다 (#120, AGENTS.md 9장)
        @Size(max = 500) String homepageUrl) {

    /** 홈페이지 주소만 바꾼 사본. 서비스에서 공통 정책으로 정규화한 값을 넣을 때 쓴다. */
    public ContestBatchItem withHomepageUrl(String normalizedHomepageUrl) {
        return new ContestBatchItem(
                sourceUrl,
                sourceSite,
                title,
                host,
                categoryCode,
                thumbnailUrl,
                startDate,
                deadline,
                description,
                normalizedHomepageUrl);
    }
}
