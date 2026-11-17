package com.gongbang.post.controller;

import com.gongbang.global.common.ApiResponse;
import com.gongbang.global.common.PageResponse;
import com.gongbang.post.dto.PostDetailResponse;
import com.gongbang.post.dto.PostListItemResponse;
import com.gongbang.post.service.PostDetailService;
import com.gongbang.post.service.PostQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 모집글 API. */
@RestController
@RequiredArgsConstructor
public class PostController {

    private final PostQueryService postQueryService;
    private final PostDetailService postDetailService;

    /** PST-001: 공개. 최신 등록순, 20개씩 (⑦, 20은 제안값). */
    @GetMapping("/api/posts")
    public ApiResponse<PageResponse<PostListItemResponse>> getPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(postQueryService.getPosts(page, size));
    }

    /** PST-005: 공개. 회원이 열면 24시간에 한 번 조회수 +1, 비회원은 올리지 않는다. */
    @GetMapping("/api/posts/{postId}")
    public ApiResponse<PostDetailResponse> getPost(@PathVariable Long postId) {
        // TODO(확인 필요): 인증 모듈(정진우)이 생기면 로그인 회원 ID를 넘긴다. 지금은 모두 비회원으로 본다.
        Long viewerId = null;
        return ApiResponse.success(postDetailService.getPost(postId, viewerId));
    }
}
