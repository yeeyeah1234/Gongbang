package com.gongbang.post.controller;

import com.gongbang.global.common.ApiResponse;
import com.gongbang.global.common.PageResponse;
import com.gongbang.post.dto.PostListItemResponse;
import com.gongbang.post.service.PostQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 모집글 API. */
@RestController
@RequiredArgsConstructor
public class PostController {

    private final PostQueryService postQueryService;

    /** PST-001: 공개. 최신 등록순, 20개씩 (⑦, 20은 제안값). */
    @GetMapping("/api/posts")
    public ApiResponse<PageResponse<PostListItemResponse>> getPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(postQueryService.getPosts(page, size));
    }
}
