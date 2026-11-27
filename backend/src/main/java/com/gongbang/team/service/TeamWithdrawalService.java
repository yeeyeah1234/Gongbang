package com.gongbang.team.service;

import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.domain.Member;
import com.gongbang.member.service.MemberLifecycleService;
import com.gongbang.post.service.PostCommandService;
import com.gongbang.team.domain.TeamRole;
import com.gongbang.team.repository.TeamMemberRepository;
import com.gongbang.team.repository.TeamRepository;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** MY-11: READ_COMMITTED인 탈퇴 트랜잭션에서만 호출한다. */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class TeamWithdrawalService {
    private final TeamRepository teams;
    private final TeamMemberRepository memberships;
    private final PostCommandService posts;
    private final TeamLeaveService leave;
    private final MemberLifecycleService lifecycle;

    public record Plan(Member member, List<Long> teamIds) {}

    public Plan prepare(Long memberId) {
        var before = teams.findWithdrawalTeams(memberId);
        before.stream()
                .map(TeamRepository.ActiveTeamMeta::getPostId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted()
                .forEach(posts::lockForWithdrawal);
        var locked = new HashSet<Long>();
        for (var meta : before) {
            if (teams.findByIdForUpdate(meta.getId()).isPresent()) locked.add(meta.getId());
        }
        // 모든 기존 모집글·팀 뒤에 회원을 잠근다. 새 팀 가입/생성도 이 회원 잠금을 통과한다.
        var member = lifecycle.activeForWithdrawal(memberId);
        var current = teams.findWithdrawalTeams(memberId);
        if (current.stream().anyMatch(meta -> !locked.contains(meta.getId())))
            throw new BusinessException(ErrorCode.INVALID_INPUT, "참여 팀 정보가 변경됐어요. 다시 탈퇴를 신청해 주세요.");
        for (var meta : current) {
            var row =
                    memberships
                            .findByTeamIdAndMemberIdAndLeftAtIsNullForUpdate(meta.getId(), memberId)
                            .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT));
            if (row.getRole() == TeamRole.LEADER)
                throw new BusinessException(ErrorCode.LEADER_CANNOT_WITHDRAW);
        }
        return new Plan(
                member, current.stream().map(TeamRepository.ActiveTeamMeta::getId).toList());
    }

    public void leaveAll(Plan plan) {
        for (Long teamId : plan.teamIds()) leave.leaveTeam(teamId, plan.member().getId());
    }
}
