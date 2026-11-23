package com.gongbang.team.controller;

import com.gongbang.auth.domain.LoginMember;
import com.gongbang.global.common.ApiResponse;
import com.gongbang.team.dto.TeamLeaderDelegateRequest;
import com.gongbang.team.dto.TeamMemberKickResponse;
import com.gongbang.team.dto.TeamMemberResponse;
import com.gongbang.team.dto.WorkspaceSummaryResponse;
import com.gongbang.team.service.TeamMemberManagementService;
import com.gongbang.team.service.TeamQueryService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 팀 API. 권한 확인은 service가 한다. */
@RestController
@RequiredArgsConstructor
public class TeamController {

    private final TeamQueryService teamQueryService;
    private final TeamMemberManagementService teamMemberManagementService;

    /** WS-02 워크스페이스 헤더 (API 명세서 GET /api/teams/{teamId}): 팀원만. */
    @GetMapping("/api/teams/{teamId}")
    public ApiResponse<WorkspaceSummaryResponse> getWorkspaceSummary(
            @AuthenticationPrincipal LoginMember me, @PathVariable Long teamId) {
        return ApiResponse.success(teamQueryService.getWorkspaceSummary(teamId, me.getMemberId()));
    }

    /** NET-06 팀원 목록 조회: 팀원만. */
    @GetMapping("/api/teams/{teamId}/members")
    public ApiResponse<List<TeamMemberResponse>> getMembers(
            @AuthenticationPrincipal LoginMember me, @PathVariable Long teamId) {
        return ApiResponse.success(
                teamMemberManagementService.getMembers(teamId, me.getMemberId()));
    }

    /** NET-06 팀원 강제 퇴장: 팀장만. */
    @DeleteMapping("/api/teams/{teamId}/members/{memberId}")
    public ApiResponse<TeamMemberKickResponse> kickMember(
            @AuthenticationPrincipal LoginMember me,
            @PathVariable Long teamId,
            @PathVariable Long memberId) {
        return ApiResponse.success(
                teamMemberManagementService.kickMember(teamId, me.getMemberId(), memberId));
    }

    /** NET-06 팀장 위임: 팀장만. */
    @PatchMapping("/api/teams/{teamId}/leader")
    public ApiResponse<Void> delegateLeader(
            @AuthenticationPrincipal LoginMember me,
            @PathVariable Long teamId,
            @Valid @RequestBody TeamLeaderDelegateRequest request) {
        teamMemberManagementService.delegateLeader(teamId, me.getMemberId(), request.memberId());
        return ApiResponse.success(null);
    }
}
