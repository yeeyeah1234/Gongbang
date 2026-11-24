package com.gongbang.workspace.sync;

/**
 * WS-03 동기화 서버용 팀 접속 권한 확인 응답 ({@code GET /internal/teams/{teamId}/access}).
 *
 * <p>leader는 현재 팀장 여부, colorIndex는 그 팀에서의 팀원 고유 색(1~8)이다.
 */
public record TeamAccessResponse(
        Long memberId, String nickname, Long teamId, boolean leader, int colorIndex) {}
