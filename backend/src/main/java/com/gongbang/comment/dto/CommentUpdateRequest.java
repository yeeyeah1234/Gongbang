package com.gongbang.comment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** NET-01·BRD-005 댓글 수정 요청 (`PATCH /api/comments/{commentId}`). */
public record CommentUpdateRequest(String content, @JsonProperty("isSecret") Boolean isSecret) {}
