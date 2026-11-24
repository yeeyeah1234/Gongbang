package com.gongbang.workspace.whiteboard.dto;

/**
 * WB-10 동기화 서버 입장 확인 (API 명세서: { whiteboardId, teamId, memberId, nickname, colorIndex }).
 * colorIndex는 그 팀에서의 팀원 색 (커서·선택 테두리 색, WB-10 ①②).
 */
public record WhiteboardAccessResponse(
        Long whiteboardId, Long teamId, Long memberId, String nickname, int colorIndex) {}
