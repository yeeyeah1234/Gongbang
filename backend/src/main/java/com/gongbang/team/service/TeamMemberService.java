package com.gongbang.team.service;

import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.team.domain.TeamRole;
import com.gongbang.team.repository.TeamMemberRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 팀원·팀장 확인 (모든 담당자가 부른다). 팀원 여부의 기준은 team_member 하나다 (AGENTS.md 6). */
@Service
@RequiredArgsConstructor
public class TeamMemberService {

    private final TeamMemberRepository teamMemberRepository;

    @Transactional(readOnly = true)
    public List<Long> findActiveMemberIds(Long teamId) {
        return java.util.stream.Stream.concat(
                        teamMemberRepository
                                .findActiveMemberIdsByRole(teamId, TeamRole.MEMBER)
                                .stream(),
                        teamMemberRepository
                                .findActiveMemberIdsByRole(teamId, TeamRole.LEADER)
                                .stream())
                .distinct()
                .toList();
    }

    /** 공지 알림을 받을 현재 일반 팀원. 팀장과 나간 팀원은 제외한다. */
    @Transactional(readOnly = true)
    public List<Long> findActiveNonLeaderMemberIds(Long teamId) {
        return teamMemberRepository.findActiveMemberIdsByRole(teamId, TeamRole.MEMBER);
    }

    /** 현재 팀원인지 (left_at IS NULL, 삭제된 팀이면 false). */
    @Transactional(readOnly = true)
    public boolean isActiveMember(Long teamId, Long memberId) {
        return teamMemberRepository.existsByTeamIdAndTeamDeletedAtIsNullAndMemberIdAndLeftAtIsNull(
                teamId, memberId);
    }

    /** 현재 팀원이면서 팀장인지 (삭제된 팀이면 false). */
    @Transactional(readOnly = true)
    public boolean isLeader(Long teamId, Long memberId) {
        return teamMemberRepository
                .existsByTeamIdAndTeamDeletedAtIsNullAndMemberIdAndRoleAndLeftAtIsNull(
                        teamId, memberId, TeamRole.LEADER);
    }

    /**
     * 이 팀에서 나간 기록(left_at IS NOT NULL)이 하나라도 있는지. leave_type은 보지 않으므로 강제 퇴장(KICKED)도 포함한다 (PST-006
     * ⑨). 지금 다시 팀원인지는 따지지 않는다.
     */
    @Transactional(readOnly = true)
    public boolean hasLeft(Long teamId, Long memberId) {
        return teamMemberRepository.existsByTeamIdAndMemberIdAndLeftAtIsNotNull(teamId, memberId);
    }

    /** 현재 팀원이 아니면 403 NOT_TEAM_MEMBER. */
    @Transactional(readOnly = true)
    public void requireMember(Long teamId, Long memberId) {
        if (!isActiveMember(teamId, memberId)) {
            throw new BusinessException(ErrorCode.NOT_TEAM_MEMBER);
        }
    }

    /** 현재 팀장이 아니면 403 NOT_TEAM_LEADER. */
    @Transactional(readOnly = true)
    public void requireLeader(Long teamId, Long memberId) {
        if (!isLeader(teamId, memberId)) {
            throw new BusinessException(ErrorCode.NOT_TEAM_LEADER);
        }
    }
}
