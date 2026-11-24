package com.gongbang.board.dto;

/** MY-02 로그인 회원의 활동 목록. */
public interface MyBoardPostItem {
    Long getBoardPostId();

    String getTitle();

    String getCategory();

    java.time.LocalDateTime getCreatedAt();
}
