package com.gongbang.comment.dto;

/** MY-02 로그인 회원의 활동 목록. */
public interface MyCommentItem {
    Long getCommentId();

    Long getPostId();

    Long getBoardPostId();

    String getPostTitle();

    String getContent();

    java.time.LocalDateTime getCreatedAt();
}
