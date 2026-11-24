package com.gongbang.comment.controller;

import com.gongbang.auth.domain.LoginMember;
import com.gongbang.comment.dto.CommentCreateRequest;
import com.gongbang.comment.dto.CommentCreateResponse;
import com.gongbang.comment.dto.CommentResponse;
import com.gongbang.comment.dto.CommentUpdateRequest;
import com.gongbang.comment.service.CommentCommandService;
import com.gongbang.comment.service.CommentQueryService;
import com.gongbang.global.common.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** NET-01·INT-004·BRD-005 모집글 및 커뮤니티 게시글 댓글·대댓글 API 컨트롤러. */
@RestController
@RequiredArgsConstructor
public class CommentController {

    private final CommentQueryService commentQueryService;
    private final CommentCommandService commentCommandService;

    /** NET-01 모집글 댓글·대댓글 목록 조회 (비회원 허용). */
    @GetMapping("/api/posts/{postId}/comments")
    public ApiResponse<List<CommentResponse>> getPostComments(
            @AuthenticationPrincipal LoginMember me, @PathVariable Long postId) {
        Long viewerId = me == null ? null : me.getMemberId();
        return ApiResponse.success(commentQueryService.getPostComments(postId, viewerId));
    }

    /** NET-01 모집글 댓글·답글 작성 (로그인 필요). */
    @PostMapping("/api/posts/{postId}/comments")
    public ApiResponse<CommentCreateResponse> createPostComment(
            @AuthenticationPrincipal LoginMember me,
            @PathVariable Long postId,
            @RequestBody(required = false) CommentCreateRequest request) {
        return ApiResponse.success(
                commentCommandService.createPostComment(postId, me.getMemberId(), request));
    }

    /** BRD-005 커뮤니티 게시글 댓글·대댓글 목록 조회 (비회원 허용). */
    @GetMapping("/api/board-posts/{boardPostId}/comments")
    public ApiResponse<List<CommentResponse>> getBoardPostComments(
            @AuthenticationPrincipal LoginMember me, @PathVariable Long boardPostId) {
        Long viewerId = me == null ? null : me.getMemberId();
        return ApiResponse.success(commentQueryService.getBoardPostComments(boardPostId, viewerId));
    }

    /** BRD-005 커뮤니티 게시글 댓글·답글 작성 (로그인 필요). */
    @PostMapping("/api/board-posts/{boardPostId}/comments")
    public ApiResponse<CommentCreateResponse> createBoardPostComment(
            @AuthenticationPrincipal LoginMember me,
            @PathVariable Long boardPostId,
            @RequestBody(required = false) CommentCreateRequest request) {
        return ApiResponse.success(
                commentCommandService.createBoardPostComment(
                        boardPostId, me.getMemberId(), request));
    }

    /** NET-01·BRD-005 댓글·답글 수정 (댓글 작성자). */
    @PatchMapping("/api/comments/{commentId}")
    public ApiResponse<CommentResponse> updateComment(
            @AuthenticationPrincipal LoginMember me,
            @PathVariable Long commentId,
            @RequestBody(required = false) CommentUpdateRequest request) {
        return ApiResponse.success(
                commentCommandService.updateComment(commentId, me.getMemberId(), request));
    }

    /** NET-01·BRD-005 댓글·답글 삭제 (댓글 작성자). */
    @DeleteMapping("/api/comments/{commentId}")
    public ApiResponse<Void> deleteComment(
            @AuthenticationPrincipal LoginMember me, @PathVariable Long commentId) {
        commentCommandService.deleteComment(commentId, me.getMemberId());
        return ApiResponse.success(null);
    }
}
