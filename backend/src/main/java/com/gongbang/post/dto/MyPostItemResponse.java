package com.gongbang.post.dto;

import com.gongbang.post.domain.PostStatus;
import com.gongbang.post.domain.RecruitPost;
import java.time.LocalDateTime;

/** MY-02 · INT-002 ③ 마이페이지 「내 글 관리」 모집글 탭 항목 (API 명세서 GET /api/members/me/posts). */
public record MyPostItemResponse(
        Long postId, String title, LocalDateTime createdAt, long commentCount, PostStatus status) {

    public static MyPostItemResponse of(RecruitPost post, long commentCount) {
        return new MyPostItemResponse(
                post.getId(), post.getTitle(), post.getCreatedAt(), commentCount, post.getStatus());
    }
}
