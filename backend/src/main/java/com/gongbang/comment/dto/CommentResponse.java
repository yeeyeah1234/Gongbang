package com.gongbang.comment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;

/** NET-01·BRD-005 댓글·대댓글 응답 DTO. 비공개 마스킹 시 `content = null`, 삭제 마스킹 시 `isDeleted = true`. */
public record CommentResponse(
        Long commentId,
        CommentAuthorResponse author,
        @JsonProperty("isPostAuthor") boolean isPostAuthor,
        String content,
        @JsonProperty("isSecret") boolean isSecret,
        @JsonProperty("isDeleted") boolean isDeleted,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<CommentResponse> replies) {}
