package com.gongbang.board.controller;

import com.gongbang.auth.domain.LoginMember;
import com.gongbang.board.domain.BoardCategory;
import com.gongbang.board.dto.BoardPostCreateResponse;
import com.gongbang.board.dto.BoardPostDetailResponse;
import com.gongbang.board.dto.BoardPostImageOrderRequest;
import com.gongbang.board.dto.BoardPostImagesResponse;
import com.gongbang.board.dto.BoardPostLikeResponse;
import com.gongbang.board.dto.BoardPostListItemResponse;
import com.gongbang.board.dto.BoardPostRequest;
import com.gongbang.board.service.BoardPostCommandService;
import com.gongbang.board.service.BoardPostImageService;
import com.gongbang.board.service.BoardPostQueryService;
import com.gongbang.global.common.ApiResponse;
import com.gongbang.global.common.PageResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 커뮤니티 게시판 API (API 명세서 BRD-001~004, BRD-006~008). 값 검증과 권한 확인은 service가 한다. */
@RestController
@RequiredArgsConstructor
public class BoardPostController {

    private final BoardPostQueryService boardPostQueryService;
    private final BoardPostCommandService boardPostCommandService;
    private final BoardPostImageService boardPostImageService;

    /** BRD-001: 공개. 말머리·공모전으로 거르고 최신순(기본)·인기순으로 정렬, 20개씩. */
    @GetMapping("/api/board-posts")
    public ApiResponse<PageResponse<BoardPostListItemResponse>> getPosts(
            @RequestParam(required = false) BoardCategory category,
            @RequestParam(required = false) Long contestId,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(
                boardPostQueryService.getPosts(category, contestId, sort, page, size));
    }

    /** BRD-008: 공개. 최근 7일 인기글 최대 3개. */
    @GetMapping("/api/board-posts/popular")
    public ApiResponse<List<BoardPostListItemResponse>> getPopular(
            @RequestParam(required = false) BoardCategory category) {
        return ApiResponse.success(boardPostQueryService.getPopular(category));
    }

    /** BRD-004: 공개. 로그인 회원이면 24시간에 한 번 조회수 +1, isLiked·isAuthor를 준다. */
    @GetMapping("/api/board-posts/{boardPostId}")
    public ApiResponse<BoardPostDetailResponse> getPost(
            @AuthenticationPrincipal LoginMember me, @PathVariable Long boardPostId) {
        Long viewerId = me == null ? null : me.getMemberId();
        return ApiResponse.success(boardPostQueryService.getPost(boardPostId, viewerId));
    }

    /** BRD-002: 로그인. */
    @PostMapping("/api/board-posts")
    public ApiResponse<BoardPostCreateResponse> create(
            @AuthenticationPrincipal LoginMember me, @RequestBody BoardPostRequest request) {
        return ApiResponse.success(boardPostCommandService.create(me.getMemberId(), request));
    }

    /** BRD-003: 작성자. 화면의 모든 값을 받아 바꾸고 수정된 상세를 돌려준다. */
    @PatchMapping("/api/board-posts/{boardPostId}")
    public ApiResponse<BoardPostDetailResponse> update(
            @AuthenticationPrincipal LoginMember me,
            @PathVariable Long boardPostId,
            @RequestBody BoardPostRequest request) {
        return ApiResponse.success(
                boardPostCommandService.update(me.getMemberId(), boardPostId, request));
    }

    /** BRD-003: 작성자. 소프트 삭제. */
    @DeleteMapping("/api/board-posts/{boardPostId}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal LoginMember me, @PathVariable Long boardPostId) {
        boardPostCommandService.delete(me.getMemberId(), boardPostId);
        return ApiResponse.success();
    }

    /** BRD-006: 작성자. 결과물 자랑 글에 사진 한 장을 맨 뒤에 붙인다 (최대 3장). 파일이 없으면 service가 400으로 막는다. */
    @PostMapping("/api/board-posts/{boardPostId}/images")
    public ApiResponse<BoardPostImagesResponse> uploadImage(
            @AuthenticationPrincipal LoginMember me,
            @PathVariable Long boardPostId,
            @RequestParam(value = "file", required = false) MultipartFile file) {
        return ApiResponse.success(
                boardPostImageService.upload(me.getMemberId(), boardPostId, file));
    }

    /** BRD-006: 작성자. 소프트 삭제, 파일은 남긴다. */
    @DeleteMapping("/api/board-posts/{boardPostId}/images/{imageId}")
    public ApiResponse<BoardPostImagesResponse> deleteImage(
            @AuthenticationPrincipal LoginMember me,
            @PathVariable Long boardPostId,
            @PathVariable Long imageId) {
        return ApiResponse.success(
                boardPostImageService.delete(me.getMemberId(), boardPostId, imageId));
    }

    /** BRD-006: 작성자. 남아 있는 사진 ID 전부를 새 순서대로 받는다 (첫 번째가 대표). */
    @PatchMapping("/api/board-posts/{boardPostId}/images/order")
    public ApiResponse<BoardPostImagesResponse> reorderImages(
            @AuthenticationPrincipal LoginMember me,
            @PathVariable Long boardPostId,
            @RequestBody BoardPostImageOrderRequest request) {
        return ApiResponse.success(
                boardPostImageService.reorder(me.getMemberId(), boardPostId, request.imageIds()));
    }

    /** BRD-007: 로그인. 내 글은 403. */
    @PutMapping("/api/board-posts/{boardPostId}/like")
    public ApiResponse<BoardPostLikeResponse> like(
            @AuthenticationPrincipal LoginMember me, @PathVariable Long boardPostId) {
        return ApiResponse.success(boardPostCommandService.like(me.getMemberId(), boardPostId));
    }

    /** BRD-007: 로그인. 좋아요 취소. */
    @DeleteMapping("/api/board-posts/{boardPostId}/like")
    public ApiResponse<BoardPostLikeResponse> unlike(
            @AuthenticationPrincipal LoginMember me, @PathVariable Long boardPostId) {
        return ApiResponse.success(boardPostCommandService.unlike(me.getMemberId(), boardPostId));
    }
}
