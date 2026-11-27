package com.gongbang.team.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.gongbang.chat.service.ChatMembershipService;
import com.gongbang.chat.service.ChatRealtimeService;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.global.websocket.TeamEventPublisher;
import com.gongbang.member.service.MemberService;
import com.gongbang.post.service.PostCommandService;
import com.gongbang.team.domain.LeaveType;
import com.gongbang.team.domain.Team;
import com.gongbang.team.domain.TeamMember;
import com.gongbang.team.domain.TeamRole;
import com.gongbang.team.repository.TeamMemberRepository;
import com.gongbang.team.repository.TeamRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TeamLeaveServiceTest {

    @Mock private TeamRepository teamRepository;
    @Mock private TeamMemberRepository teamMemberRepository;
    @Mock private MemberService memberService;
    @Mock private PostCommandService postCommandService;
    @Mock private ChatMembershipService chatMembershipService;
    @Mock private ChatRealtimeService chatRealtimeService;
    @Mock private TeamEventPublisher teamEventPublisher;
    @Mock private ApplicationEventPublisher applicationEventPublisher;

    private TeamLeaveService service;

    @BeforeEach
    void setUp() {
        service =
                new TeamLeaveService(
                        teamRepository,
                        teamMemberRepository,
                        memberService,
                        postCommandService,
                        chatMembershipService,
                        chatRealtimeService,
                        teamEventPublisher,
                        applicationEventPublisher);
    }

    @Test
    @DisplayName("없는 팀에서 나가려 하면 404 RESOURCE_NOT_FOUND이고 확정 인원을 건드리지 않는다")
    void 없는_팀이면_404() {
        given(teamRepository.findByIdAndDeletedAtIsNull(99L)).willReturn(Optional.empty());

        BusinessException ex =
                assertThrows(BusinessException.class, () -> service.leaveTeam(99L, 2L));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        verifyNoInteractions(
                postCommandService,
                chatMembershipService,
                chatRealtimeService,
                teamEventPublisher,
                applicationEventPublisher);
    }

    @Test
    @DisplayName("팀원이 아니면 403 NOT_TEAM_MEMBER이고 확정 인원을 건드리지 않는다")
    void 팀원이_아니면_403() {
        Team team = team(10L, 100L, "공방팀");
        given(teamRepository.findByIdAndDeletedAtIsNull(10L)).willReturn(Optional.of(team));
        given(teamMemberRepository.findActiveRole(10L, 2L)).willReturn(Optional.empty());

        BusinessException ex =
                assertThrows(BusinessException.class, () -> service.leaveTeam(10L, 2L));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.NOT_TEAM_MEMBER);
        verifyNoInteractions(
                postCommandService,
                chatMembershipService,
                chatRealtimeService,
                teamEventPublisher,
                applicationEventPublisher);
    }

    @Test
    @DisplayName("팀장은 바로 나갈 수 없어 409 LEADER_CANNOT_LEAVE이고 decreaseCurrentCount를 부르지 않는다")
    void 팀장은_409_거절() {
        Team team = team(10L, 100L, "공방팀");
        given(teamRepository.findByIdAndDeletedAtIsNull(10L)).willReturn(Optional.of(team));
        given(teamMemberRepository.findActiveRole(10L, 1L))
                .willReturn(Optional.of(TeamRole.LEADER));

        BusinessException ex =
                assertThrows(BusinessException.class, () -> service.leaveTeam(10L, 1L));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.LEADER_CANNOT_LEAVE);
        verify(postCommandService, never()).decreaseCurrentCount(100L);
        verifyNoInteractions(
                chatMembershipService,
                chatRealtimeService,
                teamEventPublisher,
                applicationEventPublisher);
    }

    @Test
    @DisplayName(
            "정상 나가기는 decreaseCurrentCount → team 잠금 → team_member 잠금 순서를 지키고 상태 LEFT와 이벤트를 발행한다")
    void 정상_나가기_순서와_이벤트() {
        Team team = team(10L, 100L, "공방팀");
        TeamMember member = TeamMember.member(team, 2L, 2);

        given(teamRepository.findByIdAndDeletedAtIsNull(10L)).willReturn(Optional.of(team));
        given(teamMemberRepository.findActiveRole(10L, 2L))
                .willReturn(Optional.of(TeamRole.MEMBER));
        given(postCommandService.decreaseCurrentCount(100L)).willReturn(2);
        given(teamRepository.findByIdForUpdate(10L)).willReturn(Optional.of(team));
        given(teamMemberRepository.findByTeamIdAndMemberIdAndLeftAtIsNullForUpdate(10L, 2L))
                .willReturn(Optional.of(member));
        given(memberService.displayName(2L)).willReturn("상현");

        service.leaveTeam(10L, 2L);

        assertThat(member.getLeftAt()).isNotNull();
        assertThat(member.getLeaveType()).isEqualTo(LeaveType.LEFT);

        InOrder order = inOrder(postCommandService, teamRepository, teamMemberRepository);
        order.verify(postCommandService).decreaseCurrentCount(100L);
        order.verify(teamRepository).findByIdForUpdate(10L);
        order.verify(teamMemberRepository).findByTeamIdAndMemberIdAndLeftAtIsNullForUpdate(10L, 2L);

        verify(chatMembershipService).removeMemberFromTeamRooms(10L, 2L);
        verify(chatRealtimeService).sendSystemMessage(10L, "상현님이 팀에서 나갔습니다.");
        verify(teamEventPublisher).publish(10L, "MEMBER_LEFT", 2L, 2L);
        verify(applicationEventPublisher).publishEvent(new TeamMemberLeftEvent(10L, 2L));
    }

    @Test
    @DisplayName("잠금 직전 팀장 위임을 받은 경우 잠금 후 재검증에서 409 LEADER_CANNOT_LEAVE로 거절한다")
    void 잠금_직전_팀장위임_경쟁_거절() {
        Team team = team(10L, 100L, "공방팀");
        TeamMember delegatedLeader = TeamMember.leader(team, 2L);

        given(teamRepository.findByIdAndDeletedAtIsNull(10L)).willReturn(Optional.of(team));
        given(teamMemberRepository.findActiveRole(10L, 2L))
                .willReturn(Optional.of(TeamRole.MEMBER));
        given(postCommandService.decreaseCurrentCount(100L)).willReturn(2);
        given(teamRepository.findByIdForUpdate(10L)).willReturn(Optional.of(team));
        given(teamMemberRepository.findByTeamIdAndMemberIdAndLeftAtIsNullForUpdate(10L, 2L))
                .willReturn(Optional.of(delegatedLeader));

        BusinessException ex =
                assertThrows(BusinessException.class, () -> service.leaveTeam(10L, 2L));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.LEADER_CANNOT_LEAVE);
        assertThat(delegatedLeader.getLeftAt()).isNull();
        verifyNoInteractions(
                chatMembershipService,
                chatRealtimeService,
                teamEventPublisher,
                applicationEventPublisher);
    }

    @Test
    @DisplayName(
            "2명 팀 동시 이중 나가기로 decreaseCurrentCount가 IllegalStateException을 던지면 이미 나간 상태를 확인해 403 NOT_TEAM_MEMBER를 던진다")
    void 동시_이중_나가기_IllegalStateException_변환() {
        Team team = team(10L, 100L, "공방팀");

        given(teamRepository.findByIdAndDeletedAtIsNull(10L)).willReturn(Optional.of(team));
        given(teamMemberRepository.findActiveRole(10L, 2L))
                .willReturn(Optional.of(TeamRole.MEMBER));
        given(postCommandService.decreaseCurrentCount(100L))
                .willThrow(
                        new IllegalStateException("확정 인원은 작성자를 포함해 1명 아래로 내려갈 수 없습니다: postId=100"));
        given(teamRepository.findByIdForUpdate(10L)).willReturn(Optional.of(team));
        given(teamMemberRepository.findByTeamIdAndMemberIdAndLeftAtIsNullForUpdate(10L, 2L))
                .willReturn(Optional.empty());

        BusinessException ex =
                assertThrows(BusinessException.class, () -> service.leaveTeam(10L, 2L));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.NOT_TEAM_MEMBER);
        verifyNoInteractions(
                chatMembershipService,
                chatRealtimeService,
                teamEventPublisher,
                applicationEventPublisher);
    }

    @Test
    @DisplayName("decreaseCurrentCount가 IllegalStateException을 던졌는데 여전히 일반 팀원이면 원래 예외를 그대로 던져 롤백한다")
    void decreaseCurrentCount_실제_상태이상은_원래_예외_재발생() {
        Team team = team(10L, 100L, "공방팀");
        TeamMember member = TeamMember.member(team, 2L, 2);

        given(teamRepository.findByIdAndDeletedAtIsNull(10L)).willReturn(Optional.of(team));
        given(teamMemberRepository.findActiveRole(10L, 2L))
                .willReturn(Optional.of(TeamRole.MEMBER));
        given(postCommandService.decreaseCurrentCount(100L))
                .willThrow(new IllegalStateException("팀의 모집글이 없습니다: postId=100"));
        given(teamRepository.findByIdForUpdate(10L)).willReturn(Optional.of(team));
        given(teamMemberRepository.findByTeamIdAndMemberIdAndLeftAtIsNullForUpdate(10L, 2L))
                .willReturn(Optional.of(member));

        assertThrows(IllegalStateException.class, () -> service.leaveTeam(10L, 2L));
        assertThat(member.getLeftAt()).isNull();
    }

    private static Team team(Long id, Long postId, String name) {
        Team team = Team.create(postId, name);
        ReflectionTestUtils.setField(team, "id", id);
        return team;
    }
}
