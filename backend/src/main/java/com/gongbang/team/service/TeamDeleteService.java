package com.gongbang.team.service;

import com.gongbang.chat.service.ChatMembershipService;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.global.websocket.TeamEventPublisher;
import com.gongbang.post.service.PostCommandService;
import com.gongbang.team.domain.Team;
import com.gongbang.team.domain.TeamMember;
import com.gongbang.team.domain.TeamRole;
import com.gongbang.team.repository.TeamMemberRepository;
import com.gongbang.team.repository.TeamRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** WS-08 ⑨ 팀 삭제(소프트 삭제) 서비스. 팀장 혼자 남은 팀만 삭제할 수 있다. */
@Service
@RequiredArgsConstructor
public class TeamDeleteService {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final PostCommandService postCommandService;
    private final ChatMembershipService chatMembershipService;
    private final TeamEventPublisher teamEventPublisher;
    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * WS-08 ⑨ 팀 소프트 삭제.
     *
     * <p>잠금 순서는 {@code recruit_post → team → team_member}를 지킨다. 사전 검증은 스칼라 조회로 1차 캐시에 엔티티를 올리지 않고
     * 빠르게 거절한 뒤, 잠금 읽기(FOR UPDATE)로 최신 커밋 상태를 다시 확인한다.
     */
    @Transactional
    public void deleteTeam(Long teamId, Long memberId) {
        // ① 사전 확인(잠금 없이 스칼라 조회): 404 → 403 → 409 순으로 모집글을 건드리기 전에 빠르게 거절한다.
        TeamRepository.ActiveTeamMeta teamMeta =
                teamRepository
                        .findActiveMeta(teamId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        TeamRole preRole =
                teamMemberRepository
                        .findActiveRole(teamId, memberId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_TEAM_LEADER));
        if (preRole != TeamRole.LEADER) {
            throw new BusinessException(ErrorCode.NOT_TEAM_LEADER);
        }
        if (teamMemberRepository.countByTeamIdAndLeftAtIsNull(teamId) > 1) {
            throw new BusinessException(ErrorCode.TEAM_HAS_OTHER_MEMBERS);
        }

        // ② 연결된 모집글 마감 (잠금 순서: recruit_post → team)
        LocalDateTime now = LocalDateTime.now();
        Long postId = teamMeta.getPostId();
        if (postId != null) {
            postCommandService.closeForTeamDeletion(postId, now);
        }

        // ③ team 행을 FOR UPDATE로 잠근다 (이미 삭제됐으면 404).
        Team lockedTeam =
                teamRepository
                        .findByIdForUpdate(teamId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        // ④ 팀장 권한과 활성 팀원 수를 잠금 읽기(FOR UPDATE)로 다시 확인한다(스냅샷 읽기 금지).
        List<TeamMember> activeMembers =
                teamMemberRepository.findAllByTeamIdAndLeftAtIsNullForUpdate(teamId);
        TeamMember leader =
                activeMembers.stream()
                        .filter(
                                tm ->
                                        Objects.equals(tm.getMemberId(), memberId)
                                                && tm.getRole() == TeamRole.LEADER)
                        .findFirst()
                        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_TEAM_LEADER));
        if (activeMembers.size() > 1) {
            throw new BusinessException(ErrorCode.TEAM_HAS_OTHER_MEMBERS);
        }

        // ⑤ 팀 소프트 삭제 및 팀장 행 나감 처리 (left_at IS NULL 직접 집계에서 제외되도록)
        lockedTeam.delete(now);
        leader.leave(now);

        // ⑥ 팀장을 팀 채팅방에서 정리한다 (WS-09와 같은 방식).
        chatMembershipService.removeMemberFromTeamRooms(teamId, memberId);

        // ⑦ 팀 STOMP 이벤트 TEAM_DELETED 발행 (AFTER_COMMIT에 /sub/teams/{teamId}/events로 전송)
        teamEventPublisher.publish(teamId, "TEAM_DELETED", teamId, memberId);

        // ⑧ 내부 이벤트 발행 (AFTER_COMMIT에 sync-server 연결 해제 및 알림 정리)
        applicationEventPublisher.publishEvent(new TeamDeletedEvent(teamId, memberId));
    }
}
