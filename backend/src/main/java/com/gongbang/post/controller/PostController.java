package com.gongbang.post.controller;

import com.gongbang.auth.domain.LoginMember;
import com.gongbang.global.common.ApiResponse;
import com.gongbang.global.common.PageResponse;
import com.gongbang.post.dto.PostCompleteResponse;
import com.gongbang.post.dto.PostCreateRequest;
import com.gongbang.post.dto.PostCreateResponse;
import com.gongbang.post.dto.PostDetailResponse;
import com.gongbang.post.dto.PostListItemResponse;
import com.gongbang.post.dto.PostUpdateRequest;
import com.gongbang.post.service.PostCommandService;
import com.gongbang.post.service.PostDetailService;
import com.gongbang.post.service.PostQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 모집글 API (API 명세서 PST-001~005). 값 검증과 권한 확인은 service가 한다. */
@RestController
@RequiredArgsConstructor
public class PostController {

    private final PostQueryService postQueryService;
    private final PostDetailService postDetailService;
    private final PostCommandService postCommandService;

    /** PST-001: 공개. 최신 등록순, 20개씩 (⑦, 20은 제안값). */
    @GetMapping("/api/posts")
    public ApiResponse<PageResponse<PostListItemResponse>> getPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(postQueryService.getPosts(page, size));
    }

    /** PST-005: 공개. 로그인 회원이면 24시간에 한 번 조회수 +1과 myStatus를 주고, 비회원(me null)은 조회수를 올리지 않는다. */
    @GetMapping("/api/posts/{postId}")
    public ApiResponse<PostDetailResponse> getPost(
            @AuthenticationPrincipal LoginMember me, @PathVariable Long postId) {
        Long viewerId = me == null ? null : me.getMemberId();
        return ApiResponse.success(postDetailService.getPost(postId, viewerId));
    }

    /** PST-002: 로그인. */
    @PostMapping("/api/posts")
    public ApiResponse<PostCreateResponse> create(
            @AuthenticationPrincipal LoginMember me, @RequestBody PostCreateRequest request) {
        return ApiResponse.success(postCommandService.create(me.getMemberId(), request));
    }

    /** PST-003: 작성자. 보내지 않은 값은 그대로, 수정된 상세를 돌려준다. */
    @PatchMapping("/api/posts/{postId}")
    public ApiResponse<PostDetailResponse> update(
            @AuthenticationPrincipal LoginMember me,
            @PathVariable Long postId,
            @RequestBody PostUpdateRequest request) {
        return ApiResponse.success(postCommandService.update(me.getMemberId(), postId, request));
    }

    /** PST-004: 작성자. 소프트 삭제. */
    @DeleteMapping("/api/posts/{postId}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal LoginMember me, @PathVariable Long postId) {
        postCommandService.delete(me.getMemberId(), postId);
        return ApiResponse.success();
    }

    /** REC-006: 작성자. 수동 모집 완료, 남은 대기 지원은 자동 거절. */
    @PatchMapping("/api/posts/{postId}/complete")
    public ApiResponse<PostCompleteResponse> complete(
            @AuthenticationPrincipal LoginMember me, @PathVariable Long postId) {
        return ApiResponse.success(postCommandService.complete(me.getMemberId(), postId));
    }
}
