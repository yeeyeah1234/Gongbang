package com.gongbang.team.service;

import com.gongbang.chat.service.ChatMembershipService;
import com.gongbang.chat.service.ChatRealtimeService;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.global.websocket.TeamEventPublisher;
import com.gongbang.global.websocket.UserEventPublisher;
import com.gongbang.member.dto.MemberProfileSummary;
import com.gongbang.member.service.MemberLifecycleService;
import com.gongbang.member.service.MemberService;
import com.gongbang.post.service.PostCommandService;
import com.gongbang.team.domain.Team;
import com.gongbang.team.domain.TeamMember;
import com.gongbang.team.domain.TeamRole;
import com.gongbang.team.dto.KickedEventPayload;
import com.gongbang.team.dto.TeamMemberKickResponse;
import com.gongbang.team.dto.TeamMemberResponse;
import com.gongbang.team.repository.TeamMemberRepository;
import com.gongbang.team.repository.TeamRepository;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** NET-06 팀원 목록 조회·강제 퇴장·팀장 위임 서비스. */
@Service
@RequiredArgsConstructor
public class TeamMemberManagementService {

    private final MemberLifecycleService lifecycle;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamMemberService teamMemberService;
    private final MemberService memberService;
    private final PostCommandService postCommandService;
    private final ChatMembershipService chatMembershipService;
    private final ChatRealtimeService chatRealtimeService;
    private final TeamEventPublisher teamEventPublisher;
    private final UserEventPublisher userEventPublisher;
    private final ApplicationEventPublisher applicationEventPublisher;

    /** NET-06 현재 팀원 목록을 본인 맨 위 → 팀장 → 가입순으로 조회한다. */
    @Transactional(readOnly = true)
    public List<TeamMemberResponse> getMembers(Long teamId, Long myMemberId) {
        teamMemberService.requireMember(teamId, myMemberId);

        List<TeamMember> activeMembers =
                teamMemberRepository.findAllByTeamIdAndLeftAtIsNullOrderByJoinedAtAscIdAsc(teamId);
        Map<Long, MemberProfileSummary> profiles =
                memberService.getProfileSummaries(
                        activeMembers.stream().map(TeamMember::getMemberId).toList());

        return activeMembers.stream()
                .sorted(memberOrder(myMemberId))
                .map(tm -> toMemberResponse(tm, profiles.get(tm.getMemberId())))
                .toList();
    }

    /**
     * NET-06 팀원 강제 퇴장.
     *
     * <p>모집글 확정 인원 감소는 {@code recruit_post → team} 잠금 순서를 지키기 위해 {@code team} 행을 잠그기 전에 호출한다.
     */
    @Transactional
    public TeamMemberKickResponse kickMember(Long teamId, Long actorMemberId, Long targetMemberId) {
        if (Objects.equals(actorMemberId, targetMemberId)) {
            throw new BusinessException(ErrorCode.CANNOT_KICK_SELF);
        }
        teamMemberService.requireLeader(teamId, actorMemberId);

        Team teamMeta =
                teamRepository
                        .findById(teamId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        // 잠금 순서 준수: recruit_post FOR UPDATE -> team FOR UPDATE
        int currentCount = postCommandService.decreaseCurrentCount(teamMeta.getPostId());
        Team lockedTeam =
                teamRepository
                        .findByIdForUpdate(teamId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        TeamMember target =
                teamMemberRepository
                        .findByTeamIdAndMemberIdAndLeftAtIsNull(teamId, targetMemberId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_TEAM_MEMBER));

        target.kick(LocalDateTime.now());
        chatMembershipService.removeMemberFromTeamRooms(teamId, targetMemberId);

        String targetNickname = memberService.displayName(targetMemberId);
        chatRealtimeService.sendSystemMessage(teamId, targetNickname + "님이 팀에서 강제 퇴장되었습니다.");

        teamEventPublisher.publish(teamId, "MEMBER_KICKED", targetMemberId, actorMemberId);
        userEventPublisher.publish(
                targetMemberId, "KICKED", new KickedEventPayload(teamId, lockedTeam.getName()));
        applicationEventPublisher.publishEvent(new TeamMemberKickedEvent(teamId, targetMemberId));

        return new TeamMemberKickResponse(currentCount);
    }

    /** NET-06 팀장 위임. 현재 팀장과 대상 팀원의 역할을 하나의 트랜잭션에서 맞바꾼다. */
    @Transactional
    public void delegateLeader(Long teamId, Long actorMemberId, Long targetMemberId) {
        teamMemberService.requireLeader(teamId, actorMemberId);
        if (Objects.equals(actorMemberId, targetMemberId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "자기 자신에게는 팀장을 위임할 수 없습니다.");
        }

        teamRepository
                .findByIdForUpdate(teamId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        lifecycle.lockActiveMembers(List.of(targetMemberId));

        TeamMember currentLeader =
                teamMemberRepository
                        .findByTeamIdAndMemberIdAndLeftAtIsNullForUpdate(teamId, actorMemberId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_TEAM_LEADER));
        if (currentLeader.getRole() != TeamRole.LEADER) {
            throw new BusinessException(ErrorCode.NOT_TEAM_LEADER);
        }

        TeamMember newLeader =
                teamMemberRepository
                        .findByTeamIdAndMemberIdAndLeftAtIsNullForUpdate(teamId, targetMemberId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_TEAM_MEMBER));

        currentLeader.changeRole(TeamRole.MEMBER);
        newLeader.changeRole(TeamRole.LEADER);

        String newLeaderNickname = memberService.displayName(targetMemberId);
        chatRealtimeService.sendSystemMessage(teamId, newLeaderNickname + "님이 새로운 팀장이 되었습니다.");
        teamEventPublisher.publish(teamId, "LEADER_CHANGED", targetMemberId, actorMemberId);
    }

    private static Comparator<TeamMember> memberOrder(Long myMemberId) {
        return Comparator.<TeamMember, Integer>comparing(
                        tm -> Objects.equals(tm.getMemberId(), myMemberId) ? 0 : 1)
                .thenComparingInt(tm -> tm.getRole() == TeamRole.LEADER ? 0 : 1);
    }

    private static TeamMemberResponse toMemberResponse(
            TeamMember tm, MemberProfileSummary profile) {
        String nickname = profile != null ? profile.nickname() : "탈퇴한 사용자";
        String profileImageUrl = profile != null ? profile.profileImageUrl() : null;
        List<String> desiredRoles = profile != null ? profile.desiredRoles() : List.of();
        return new TeamMemberResponse(
                tm.getMemberId(),
                nickname,
                profileImageUrl,
                tm.getRole(),
                tm.getColorIndex(),
                desiredRoles);
    }
}
