package com.gongbang.team.dto;

import jakarta.validation.constraints.NotNull;

/** NET-06 팀장 위임 요청 (`PATCH /api/teams/{teamId}/leader`). */
public record TeamLeaderDelegateRequest(@NotNull Long memberId) {}
