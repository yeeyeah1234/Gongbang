package com.gongbang.application.dto;

import com.gongbang.post.domain.PostStatus;
import com.gongbang.post.dto.TeamJoinResult;

/** REC-004 수락 응답 (API 명세서 { teamId, currentCount, targetCount, postStatus }). */
public record ApplicationAcceptResponse(
        Long teamId, int currentCount, int targetCount, PostStatus postStatus) {

    public static ApplicationAcceptResponse from(TeamJoinResult result) {
        return new ApplicationAcceptResponse(
                result.teamId(), result.currentCount(), result.targetCount(), result.postStatus());
    }
}
