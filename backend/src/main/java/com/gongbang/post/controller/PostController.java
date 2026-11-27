package com.gongbang.post.controller;

import com.gongbang.auth.domain.LoginMember;
import com.gongbang.global.common.ApiResponse;
import com.gongbang.global.common.PageResponse;
import com.gongbang.post.dto.PostCompleteResponse;
import com.gongbang.post.dto.PostCreateRequest;
import com.gongbang.post.dto.PostCreateResponse;
import com.gongbang.post.dto.PostDetailResponse;
import com.gongbang.post.dto.PostImageOrderRequest;
import com.gongbang.post.dto.PostImagesResponse;
import com.gongbang.post.dto.PostListItemResponse;
import com.gongbang.post.dto.PostUpdateRequest;
import com.gongbang.post.service.PostCommandService;
import com.gongbang.post.service.PostDetailService;
import com.gongbang.post.service.PostImageService;
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
import org.springframework.web.multipart.MultipartFile;

/** 모집글 API (API 명세서 PST-001~005). 값 검증과 권한 확인은 service가 한다. */
@RestController
@RequiredArgsConstructor
public class PostController {

    private final PostQueryService postQueryService;
    private final PostDetailService postDetailService;
    private final PostCommandService postCommandService;
    private final PostImageService postImageService;

    /** PST-001: 공개. 최신 등록순, 20개씩 (⑦, 20은 제안값). ⑩ contestId·recruiting으로 거른다. */
    @GetMapping("/api/posts")
    public ApiResponse<PageResponse<PostListItemResponse>> getPosts(
            @RequestParam(required = false) Long contestId,
            @RequestParam(defaultValue = "false") boolean recruiting,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(postQueryService.getPosts(contestId, recruiting, page, size));
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

    /** PST-007: 작성자. 외부 공모전 모집글에 사진 한 장을 맨 뒤에 붙인다 (최대 3장). 파일이 없으면 service가 400으로 막는다. */
    @PostMapping("/api/posts/{postId}/images")
    public ApiResponse<PostImagesResponse> uploadImage(
            @AuthenticationPrincipal LoginMember me,
            @PathVariable Long postId,
            @RequestParam(value = "file", required = false) MultipartFile file) {
        return ApiResponse.success(postImageService.upload(me.getMemberId(), postId, file));
    }

    /** PST-007: 작성자. 소프트 삭제, 파일은 남긴다. */
    @DeleteMapping("/api/posts/{postId}/images/{imageId}")
    public ApiResponse<PostImagesResponse> deleteImage(
            @AuthenticationPrincipal LoginMember me,
            @PathVariable Long postId,
            @PathVariable Long imageId) {
        return ApiResponse.success(postImageService.delete(me.getMemberId(), postId, imageId));
    }

    /** PST-007: 작성자. 남아 있는 사진 ID 전부를 새 순서대로 받는다 (첫 번째가 대표). */
    @PatchMapping("/api/posts/{postId}/images/order")
    public ApiResponse<PostImagesResponse> reorderImages(
            @AuthenticationPrincipal LoginMember me,
            @PathVariable Long postId,
            @RequestBody PostImageOrderRequest request) {
        return ApiResponse.success(
                postImageService.reorder(me.getMemberId(), postId, request.imageIds()));
    }

    /** REC-006: 작성자. 수동 모집 완료, 남은 대기 지원은 자동 거절. */
    @PatchMapping("/api/posts/{postId}/complete")
    public ApiResponse<PostCompleteResponse> complete(
            @AuthenticationPrincipal LoginMember me, @PathVariable Long postId) {
        return ApiResponse.success(postCommandService.complete(me.getMemberId(), postId));
    }
}
