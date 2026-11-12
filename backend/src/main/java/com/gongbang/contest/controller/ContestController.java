package com.gongbang.contest.controller;

import com.gongbang.contest.dto.CategoryResponse;
import com.gongbang.contest.dto.ContestCardResponse;
import com.gongbang.contest.service.ContestQueryService;
import com.gongbang.global.common.ApiResponse;
import com.gongbang.global.common.PageResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 공모전 공개 API (비회원도 조회 가능). */
@RestController
@RequiredArgsConstructor
public class ContestController {

    private final ContestQueryService contestQueryService;

    /** MAIN-01·04·05: 기본은 진행 중 공모전, 최신순, 20개씩. */
    @GetMapping("/api/contests")
    public ApiResponse<PageResponse<ContestCardResponse>> getContests(
            @RequestParam(defaultValue = "active") String status,
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(
                contestQueryService.getContests(status, sort, category, keyword, page, size));
    }

    /** MAIN-05 */
    @GetMapping("/api/categories")
    public ApiResponse<List<CategoryResponse>> getCategories() {
        return ApiResponse.success(contestQueryService.getCategories());
    }
}
