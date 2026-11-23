package com.gongbang.team.dto;

/** WS-08 팀 이름 변경 결과 (API 명세서 PATCH /api/teams/{teamId}). */
public record TeamRenameResponse(Long teamId, String name) {}
