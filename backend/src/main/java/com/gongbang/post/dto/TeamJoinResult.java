package com.gongbang.post.dto;

import com.gongbang.post.domain.PostStatus;
import com.gongbang.post.domain.RecruitPost;

/** INT-005 확정 팀원 등록 결과. 정원이 차서 모집완료가 되면 postStatus가 COMPLETED. */
public record TeamJoinResult(
        Long teamId, int currentCount, int targetCount, PostStatus postStatus) {

    public static TeamJoinResult of(Long teamId, RecruitPost post) {
        return new TeamJoinResult(
                teamId, post.getCurrentCount(), post.getTargetCount(), post.getStatus());
    }
}
