package com.gongbang.search.controller;

import com.gongbang.global.common.ApiResponse;
import com.gongbang.search.dto.SearchResponse;
import com.gongbang.search.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 통합 검색 (공개, API 명세서 SRCH-02·05). */
@RestController
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @GetMapping("/api/search")
    public ApiResponse<SearchResponse> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "all") String type,
            @RequestParam(defaultValue = "accuracy") String sort,
            @RequestParam(defaultValue = "false") boolean includeClosed,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(
                searchService.search(keyword, type, sort, includeClosed, page, size));
    }
}
