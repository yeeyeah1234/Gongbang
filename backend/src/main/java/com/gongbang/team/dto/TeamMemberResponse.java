package com.gongbang.team.dto;

import com.gongbang.team.domain.TeamRole;
import java.util.List;

/** NET-06 팀원 목록 항목 응답 (`GET /api/teams/{teamId}/members`). */
public record TeamMemberResponse(
        Long memberId,
        String nickname,
        String profileImageUrl,
        TeamRole role,
        int colorIndex,
        List<String> desiredRoles) {}
