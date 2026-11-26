package com.gongbang.team.service;

import com.gongbang.chat.service.ChatMembershipService;
import com.gongbang.chat.service.ChatRealtimeService;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.global.websocket.TeamEventPublisher;
import com.gongbang.member.service.MemberService;
import com.gongbang.post.service.PostCommandService;
import com.gongbang.team.domain.Team;
import com.gongbang.team.domain.TeamMember;
import com.gongbang.team.domain.TeamRole;
import com.gongbang.team.repository.TeamMemberRepository;
import com.gongbang.team.repository.TeamRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** WS-09 팀 나가기 서비스. */
@Service
@RequiredArgsConstructor
public class TeamLeaveService {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final MemberService memberService;
    private final PostCommandService postCommandService;
    private final ChatMembershipService chatMembershipService;
    private final ChatRealtimeService chatRealtimeService;
    private final TeamEventPublisher teamEventPublisher;
    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * WS-09 팀 나가기.
     *
     * <p>잠금 순서는 {@code recruit_post → team → team_member}를 지킨다 (INT-006 ④). 팀장·비팀원은 {@code
     * decreaseCurrentCount}를 호출하기 전에 먼저 거절하고, 잠금 뒤에도 최신 커밋 상태를 다시 확인해 동시 이중 요청과 동시 팀장 위임을 막는다.
     */
    @Transactional
    public void leaveTeam(Long teamId, Long memberId) {
        Team teamMeta =
                teamRepository
                        .findById(teamId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        // 사전 검증(스칼라 조회): 팀장·비팀원 요청은 recruit_post 잠금·감소 전에 거절한다.
        // 엔티티가 아닌 role만 읽어 1차 캐시에 TeamMember를 올리지 않으므로, 뒤의 FOR UPDATE 재조회가 최신 커밋 상태를 읽는다.
        TeamRole preRole =
                teamMemberRepository
                        .findActiveRole(teamId, memberId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_TEAM_MEMBER));
        if (preRole == TeamRole.LEADER) {
            throw new BusinessException(ErrorCode.LEADER_CANNOT_LEAVE);
        }

        // 잠금 순서 준수: recruit_post FOR UPDATE -> team FOR UPDATE -> team_member FOR UPDATE
        try {
            postCommandService.decreaseCurrentCount(teamMeta.getPostId());
        } catch (IllegalStateException e) {
            // 2명 팀(팀장 1 + 팀원 1)에서 같은 팀원이 동시에 두 번 나가기를 요청하면,
            // 첫 요청이 current_count를 2→1로 커밋한 뒤 두 번째 요청이 decreaseCurrentCount에서
            // IllegalStateException(확정 인원은 1명 아래로 감소 불가)을 만날 수 있다.
            // 예외를 잡더라도 트랜잭션 전체가 반드시 롤백되도록 항상 BusinessException 또는 원래 예외를 다시 던진다
            // (절대 정상 흐름으로 넘어가지 않는다).
            teamRepository
                    .findByIdForUpdate(teamId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
            TeamMember lockedMember =
                    teamMemberRepository
                            .findByTeamIdAndMemberIdAndLeftAtIsNullForUpdate(teamId, memberId)
                            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_TEAM_MEMBER));
            if (lockedMember.getRole() == TeamRole.LEADER) {
                throw new BusinessException(ErrorCode.LEADER_CANNOT_LEAVE);
            }
            throw e;
        }

        teamRepository
                .findByIdForUpdate(teamId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        // 동시 이중 나가기·동시 팀장 위임 방어: 잠금 뒤 최신 커밋 상태를 다시 확인한다.
        // 여기서 예외가 발생하면 decreaseCurrentCount의 변경도 같은 트랜잭션에서 함께 롤백된다.
        TeamMember activeMember =
                teamMemberRepository
                        .findByTeamIdAndMemberIdAndLeftAtIsNullForUpdate(teamId, memberId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_TEAM_MEMBER));
        if (activeMember.getRole() == TeamRole.LEADER) {
            throw new BusinessException(ErrorCode.LEADER_CANNOT_LEAVE);
        }

        activeMember.leave(LocalDateTime.now());
        chatMembershipService.removeMemberFromTeamRooms(teamId, memberId);

        String nickname = memberService.displayName(memberId);
        // TODO(확인 필요): 기능정의서에 팀 나가기 채팅 시스템 메시지 문구가 없어 강제 퇴장 문구에 맞춰 정함
        chatRealtimeService.sendSystemMessage(teamId, nickname + "님이 팀에서 나갔습니다.");

        teamEventPublisher.publish(teamId, "MEMBER_LEFT", memberId, memberId);
        applicationEventPublisher.publishEvent(new TeamMemberLeftEvent(teamId, memberId));
    }
}
