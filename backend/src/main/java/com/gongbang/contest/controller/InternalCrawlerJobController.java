package com.gongbang.contest.controller;

import com.gongbang.contest.service.AdminCrawlerService;
import com.gongbang.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/contests/jobs")
public class InternalCrawlerJobController {
    private final AdminCrawlerService service;

    public record Result(boolean success) {}

    @PostMapping("/claim")
    public ApiResponse<?> claim() {
        return ApiResponse.success(service.claim());
    }

    @PostMapping("/{id}/finish")
    public ApiResponse<?> finish(@PathVariable Long id, @RequestBody Result body) {
        service.finish(id, body.success());
        return ApiResponse.success();
    }
}
