package com.gongbang.workspace.sync;

import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.domain.Member;
import com.gongbang.member.service.MemberService;
import com.gongbang.team.domain.TeamRole;
import com.gongbang.team.dto.TeamMemberResponse;
import com.gongbang.team.service.TeamMemberManagementService;
import com.gongbang.team.service.TeamService;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 동기화 서버(sync-server, Hocuspocus)가 워크스페이스 접속자 방({@code team:{teamId}}) 입장 시 부르는 팀 권한 확인 서비스
 * (WS-03). X-Sync-Key 확인은 {@code SyncKeyFilter}가 컨트롤러 앞에서 끝낸다.
 */
@Service
@RequiredArgsConstructor
public class TeamSyncService {

    private final TeamService teamService;
    private final TeamMemberManagementService teamMemberManagementService;
    private final MemberService memberService;

    /**
     * WS-03 팀 접속 권한 확인. 문서 access({@code DocumentSyncService.checkAccess})와 같은 순서다.
     *
     * <p>순서: 토큰 없음·틀림(null)이거나 활동 회원이 아니면 401 UNAUTHORIZED → 팀이 없으면 404 RESOURCE_NOT_FOUND → 그 팀의
     * 현재 팀원이 아니면 403 NOT_TEAM_MEMBER.
     */
    @Transactional(readOnly = true)
    public TeamAccessResponse checkAccess(Long memberId, Long teamId) {
        if (memberId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        Member member =
                memberService
                        .findActiveMember(memberId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        teamService.requireExists(teamId);

        TeamMemberResponse teamMember =
                teamMemberManagementService.getMembers(teamId, memberId).stream()
                        .filter(item -> Objects.equals(item.memberId(), memberId))
                        .findFirst()
                        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_TEAM_MEMBER));

        return new TeamAccessResponse(
                memberId,
                member.getNickname(),
                teamId,
                teamMember.role() == TeamRole.LEADER,
                teamMember.colorIndex());
    }
}
