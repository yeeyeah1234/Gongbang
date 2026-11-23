package com.gongbang.team.dto;

/** NET-06 팀원 강제 퇴장 응답 (`DELETE /api/teams/{teamId}/members/{memberId}`). */
public record TeamMemberKickResponse(int currentCount) {}
