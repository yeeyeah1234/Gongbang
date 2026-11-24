package com.gongbang.comment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * NET-01·BRD-005 댓글·답글 작성 요청 (`POST /api/posts/{postId}/comments`, `POST
 * /api/board-posts/{boardPostId}/comments`).
 */
public record CommentCreateRequest(
        String content, @JsonProperty("isSecret") Boolean isSecret, Long parentId) {}
