package com.gongbang.invitation.dto;

import com.gongbang.post.domain.RecruitPost;

/** NET-02 초대할 내 모집글 목록 항목 (`GET /api/members/me/recruiting-posts`). */
public record RecruitingPostResponse(Long postId, String title, int currentCount, int targetCount) {

    public static RecruitingPostResponse from(RecruitPost post) {
        return new RecruitingPostResponse(
                post.getId(), post.getTitle(), post.getCurrentCount(), post.getTargetCount());
    }
}
