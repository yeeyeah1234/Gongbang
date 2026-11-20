package com.gongbang.team.service;

import com.gongbang.team.dto.WorkspaceSummaryResponse;
import com.gongbang.team.repository.TeamRepository;
import com.gongbang.team.repository.TeamWorkspaceSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 팀 조회 (WS-02 워크스페이스 헤더). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamQueryService {

    private final TeamRepository teamRepository;
    private final TeamMemberService teamMemberService;

    /**
     * 현재 팀원만 본다. 팀원이 아니거나 없는 팀이면 403 NOT_TEAM_MEMBER (팀 이름 등이 새지 않게 먼저 확인한다, WS-01 ②).
     *
     * <p>TODO(확인 필요): 없는 팀을 404로 나눌지. 문서에 TEAM_NOT_FOUND 같은 코드가 없어 403과 같게 둔다.
     */
    public WorkspaceSummaryResponse getWorkspaceSummary(Long teamId, Long memberId) {
        teamMemberService.requireMember(teamId, memberId);

        TeamWorkspaceSummary summary =
                teamRepository
                        .findWorkspaceSummary(teamId)
                        // 현재 팀원이 있으면 팀 행도 있다 (team_member.team_id FK). 정상 흐름에서는 생기지 않는다.
                        .orElseThrow(() -> new IllegalStateException("없는 팀입니다: teamId=" + teamId));

        return new WorkspaceSummaryResponse(
                teamId,
                summary.getTeamName(),
                summary.getContestTitle(),
                summary.deadlineDate(),
                teamMemberService.isLeader(teamId, memberId),
                memberId);
    }
}
