package com.gongbang.contest.controller;

import com.gongbang.contest.dto.ContestBatchRequest;
import com.gongbang.contest.dto.ContestBatchResponse;
import com.gongbang.contest.service.ContestBatchService;
import com.gongbang.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * CRW-01: 크롤러 전용 내부 API. {@link com.gongbang.contest.config.CrawlerKeyFilter}가 크롤러 전용 키를 먼저 검증한다.
 */
@RestController
@RequestMapping("/internal/contests")
@RequiredArgsConstructor
public class InternalContestController {

    private final ContestBatchService contestBatchService;

    @PostMapping("/batch")
    public ApiResponse<ContestBatchResponse> batch(
            @Valid @RequestBody ContestBatchRequest request) {
        return ApiResponse.success(contestBatchService.upsert(request.contests()));
    }
}
