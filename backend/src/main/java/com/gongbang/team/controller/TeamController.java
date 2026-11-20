package com.gongbang.team.controller;

import com.gongbang.auth.domain.LoginMember;
import com.gongbang.global.common.ApiResponse;
import com.gongbang.team.dto.WorkspaceSummaryResponse;
import com.gongbang.team.service.TeamQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** 팀 API. 권한 확인은 service가 한다. */
@RestController
@RequiredArgsConstructor
public class TeamController {

    private final TeamQueryService teamQueryService;

    /** WS-02 워크스페이스 헤더 (명세서에 없어 추가): 팀원만. */
    @GetMapping("/api/teams/{teamId}/workspace-summary")
    public ApiResponse<WorkspaceSummaryResponse> getWorkspaceSummary(
            @AuthenticationPrincipal LoginMember me, @PathVariable Long teamId) {
        return ApiResponse.success(teamQueryService.getWorkspaceSummary(teamId, me.getMemberId()));
    }
}
