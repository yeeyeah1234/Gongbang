package com.gongbang.application.dto;

/** MY-02 로그인 회원의 활동 목록. */
public interface MyApplicationListItem {
    Long getApplicationId();

    Long getPostId();

    String getTitle();

    String getStatus();

    java.time.LocalDateTime getDeletedAt();

    java.time.LocalDateTime getCreatedAt();
}
