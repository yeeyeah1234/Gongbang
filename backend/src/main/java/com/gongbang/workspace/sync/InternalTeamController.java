package com.gongbang.workspace.sync;

import com.gongbang.auth.domain.LoginMember;
import com.gongbang.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 동기화 서버 전용 팀 접속 권한 API (WS-03). X-Sync-Key가 틀리면 {@code SyncKeyFilter}가 여기 오기 전에 401
 * INVALID_INTERNAL_KEY로 막는다 (AGENTS.md 9).
 *
 * <p>동기화 서버가 브라우저에게 받은 access 토큰을 {@code Authorization: Bearer ...}로 그대로 넘기면 {@code
 * JwtAuthenticationFilter}가 {@link LoginMember}를 넣어 준다. 키 없이 토큰만으로는 열리지 않는다.
 */
@RestController
@RequiredArgsConstructor
public class InternalTeamController {

    private final TeamSyncService teamSyncService;

    /** WS-03 워크스페이스 접속자 방({@code team:{teamId}}) 입장 권한 확인. */
    @GetMapping("/internal/teams/{teamId}/access")
    public ApiResponse<TeamAccessResponse> checkAccess(
            @AuthenticationPrincipal LoginMember me, @PathVariable Long teamId) {
        Long memberId = me == null ? null : me.getMemberId();
        return ApiResponse.success(teamSyncService.checkAccess(memberId, teamId));
    }
}
