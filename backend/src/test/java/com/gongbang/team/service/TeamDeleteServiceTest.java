package com.gongbang.team.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.gongbang.chat.service.ChatMembershipService;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.global.websocket.TeamEventPublisher;
import com.gongbang.post.service.PostCommandService;
import com.gongbang.team.domain.LeaveType;
import com.gongbang.team.domain.Team;
import com.gongbang.team.domain.TeamMember;
import com.gongbang.team.domain.TeamRole;
import com.gongbang.team.repository.TeamMemberRepository;
import com.gongbang.team.repository.TeamRepository;
import java.util.List;
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
class TeamDeleteServiceTest {

    @Mock private TeamRepository teamRepository;
    @Mock private TeamMemberRepository teamMemberRepository;
    @Mock private PostCommandService postCommandService;
    @Mock private ChatMembershipService chatMembershipService;
    @Mock private TeamEventPublisher teamEventPublisher;
    @Mock private ApplicationEventPublisher applicationEventPublisher;

    private TeamDeleteService service;

    @BeforeEach
    void setUp() {
        service =
                new TeamDeleteService(
                        teamRepository,
                        teamMemberRepository,
                        postCommandService,
                        chatMembershipService,
                        teamEventPublisher,
                        applicationEventPublisher);
    }

    @Test
    @DisplayName("없는 팀이거나 이미 삭제된 팀이면 404 RESOURCE_NOT_FOUND")
    void 없는_팀이거나_삭제된_팀이면_404() {
        given(teamRepository.findActiveMeta(99L)).willReturn(Optional.empty());

        BusinessException ex =
                assertThrows(BusinessException.class, () -> service.deleteTeam(99L, 1L));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        verifyNoInteractions(
                teamMemberRepository,
                postCommandService,
                chatMembershipService,
                teamEventPublisher,
                applicationEventPublisher);
    }

    @Test
    @DisplayName("팀원이 아니면 403 NOT_TEAM_LEADER")
    void 비팀원이면_403() {
        given(teamRepository.findActiveMeta(10L)).willReturn(Optional.of(meta(10L, 100L)));
        given(teamMemberRepository.findActiveRole(10L, 2L)).willReturn(Optional.empty());

        BusinessException ex =
                assertThrows(BusinessException.class, () -> service.deleteTeam(10L, 2L));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.NOT_TEAM_LEADER);
        verifyNoInteractions(
                postCommandService,
                chatMembershipService,
                teamEventPublisher,
                applicationEventPublisher);
    }

    @Test
    @DisplayName("일반 팀원이면 403 NOT_TEAM_LEADER")
    void 일반_팀원이면_403() {
        given(teamRepository.findActiveMeta(10L)).willReturn(Optional.of(meta(10L, 100L)));
        given(teamMemberRepository.findActiveRole(10L, 2L))
                .willReturn(Optional.of(TeamRole.MEMBER));

        BusinessException ex =
                assertThrows(BusinessException.class, () -> service.deleteTeam(10L, 2L));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.NOT_TEAM_LEADER);
        verifyNoInteractions(
                postCommandService,
                chatMembershipService,
                teamEventPublisher,
                applicationEventPublisher);
    }

    @Test
    @DisplayName("활성 팀원이 2명 이상이면 409 TEAM_HAS_OTHER_MEMBERS")
    void 활성_팀원이_2명_이상이면_409() {
        given(teamRepository.findActiveMeta(10L)).willReturn(Optional.of(meta(10L, 100L)));
        given(teamMemberRepository.findActiveRole(10L, 1L))
                .willReturn(Optional.of(TeamRole.LEADER));
        given(teamMemberRepository.countByTeamIdAndLeftAtIsNull(10L)).willReturn(2L);

        BusinessException ex =
                assertThrows(BusinessException.class, () -> service.deleteTeam(10L, 1L));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.TEAM_HAS_OTHER_MEMBERS);
        verify(teamRepository, never()).findByIdForUpdate(10L);
        verifyNoInteractions(
                postCommandService,
                chatMembershipService,
                teamEventPublisher,
                applicationEventPublisher);
    }

    @Test
    @DisplayName(
            "정상 삭제: 모집글 마감 후 팀 잠금·deletedAt·팀장 leftAt을 기록하고 TEAM_DELETED·TeamDeletedEvent만 발행한다")
    void 정상_삭제_상태와_이벤트() {
        Team team = team(10L, 100L, "공방팀");
        TeamMember leader = TeamMember.leader(team, 1L);

        given(teamRepository.findActiveMeta(10L)).willReturn(Optional.of(meta(10L, 100L)));
        given(teamMemberRepository.findActiveRole(10L, 1L))
                .willReturn(Optional.of(TeamRole.LEADER));
        given(teamMemberRepository.countByTeamIdAndLeftAtIsNull(10L)).willReturn(1L);
        given(teamRepository.findByIdForUpdate(10L)).willReturn(Optional.of(team));
        given(teamMemberRepository.findAllByTeamIdAndLeftAtIsNullForUpdate(10L))
                .willReturn(List.of(leader));

        service.deleteTeam(10L, 1L);

        assertThat(team.isDeleted()).isTrue();
        assertThat(team.getDeletedAt()).isNotNull();
        assertThat(leader.getLeftAt()).isNotNull();
        assertThat(leader.getLeaveType()).isEqualTo(LeaveType.LEFT);

        InOrder order = inOrder(postCommandService, teamRepository, teamMemberRepository);
        order.verify(postCommandService).closeForTeamDeletion(100L, team.getDeletedAt());
        order.verify(teamRepository).findByIdForUpdate(10L);
        order.verify(teamMemberRepository).findAllByTeamIdAndLeftAtIsNullForUpdate(10L);

        verify(chatMembershipService).removeMemberFromTeamRooms(10L, 1L);
        verify(teamEventPublisher).publish(10L, "TEAM_DELETED", 10L, 1L);
        verify(teamEventPublisher, never()).publish(eq(10L), eq("MEMBER_LEFT"), any(), any());
        verify(applicationEventPublisher).publishEvent(new TeamDeletedEvent(10L, 1L));
        verify(applicationEventPublisher, never()).publishEvent(any(TeamMemberLeftEvent.class));
    }

    @Test
    @DisplayName("연결된 모집글이 없는 팀(postId null)은 closeForTeamDeletion을 부르지 않고 삭제한다")
    void 모집글이_없는_팀이면_모집글_마감을_부르지_않는다() {
        Team team = team(10L, null, "공방팀");
        TeamMember leader = TeamMember.leader(team, 1L);

        given(teamRepository.findActiveMeta(10L)).willReturn(Optional.of(meta(10L, null)));
        given(teamMemberRepository.findActiveRole(10L, 1L))
                .willReturn(Optional.of(TeamRole.LEADER));
        given(teamMemberRepository.countByTeamIdAndLeftAtIsNull(10L)).willReturn(1L);
        given(teamRepository.findByIdForUpdate(10L)).willReturn(Optional.of(team));
        given(teamMemberRepository.findAllByTeamIdAndLeftAtIsNullForUpdate(10L))
                .willReturn(List.of(leader));

        service.deleteTeam(10L, 1L);

        assertThat(team.isDeleted()).isTrue();
        assertThat(leader.getLeftAt()).isNotNull();
        verifyNoInteractions(postCommandService);
        verify(teamRepository).findByIdForUpdate(10L);
        verify(chatMembershipService).removeMemberFromTeamRooms(10L, 1L);
        verify(teamEventPublisher).publish(10L, "TEAM_DELETED", 10L, 1L);
        verify(applicationEventPublisher).publishEvent(new TeamDeletedEvent(10L, 1L));
    }

    @Test
    @DisplayName("잠금 직전 이미 삭제됐으면 404 RESOURCE_NOT_FOUND")
    void 잠금_직전_이미_삭제됐으면_404() {
        given(teamRepository.findActiveMeta(10L)).willReturn(Optional.of(meta(10L, 100L)));
        given(teamMemberRepository.findActiveRole(10L, 1L))
                .willReturn(Optional.of(TeamRole.LEADER));
        given(teamMemberRepository.countByTeamIdAndLeftAtIsNull(10L)).willReturn(1L);
        given(teamRepository.findByIdForUpdate(10L)).willReturn(Optional.empty());

        BusinessException ex =
                assertThrows(BusinessException.class, () -> service.deleteTeam(10L, 1L));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        verify(teamMemberRepository, never()).findAllByTeamIdAndLeftAtIsNullForUpdate(10L);
        verifyNoInteractions(chatMembershipService, teamEventPublisher, applicationEventPublisher);
    }

    @Test
    @DisplayName("잠금 직전 다른 팀원이 합류했으면 잠금 읽기 재검증에서 409 TEAM_HAS_OTHER_MEMBERS")
    void 잠금_직전_팀원_합류시_409() {
        Team team = team(10L, 100L, "공방팀");
        TeamMember leader = TeamMember.leader(team, 1L);
        TeamMember newcomer = TeamMember.member(team, 2L, 2);

        given(teamRepository.findActiveMeta(10L)).willReturn(Optional.of(meta(10L, 100L)));
        given(teamMemberRepository.findActiveRole(10L, 1L))
                .willReturn(Optional.of(TeamRole.LEADER));
        given(teamMemberRepository.countByTeamIdAndLeftAtIsNull(10L)).willReturn(1L);
        given(teamRepository.findByIdForUpdate(10L)).willReturn(Optional.of(team));
        given(teamMemberRepository.findAllByTeamIdAndLeftAtIsNullForUpdate(10L))
                .willReturn(List.of(leader, newcomer));

        BusinessException ex =
                assertThrows(BusinessException.class, () -> service.deleteTeam(10L, 1L));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.TEAM_HAS_OTHER_MEMBERS);
        assertThat(team.isDeleted()).isFalse();
        assertThat(leader.getLeftAt()).isNull();
        verifyNoInteractions(chatMembershipService, teamEventPublisher, applicationEventPublisher);
    }

    @Test
    @DisplayName("잠금 직전 팀장 권한이 바뀌었으면 잠금 읽기 재검증에서 403 NOT_TEAM_LEADER")
    void 잠금_직전_팀장_권한_상실시_403() {
        Team team = team(10L, 100L, "공방팀");
        TeamMember demoted = TeamMember.member(team, 1L, 1);

        given(teamRepository.findActiveMeta(10L)).willReturn(Optional.of(meta(10L, 100L)));
        given(teamMemberRepository.findActiveRole(10L, 1L))
                .willReturn(Optional.of(TeamRole.LEADER));
        given(teamMemberRepository.countByTeamIdAndLeftAtIsNull(10L)).willReturn(1L);
        given(teamRepository.findByIdForUpdate(10L)).willReturn(Optional.of(team));
        given(teamMemberRepository.findAllByTeamIdAndLeftAtIsNullForUpdate(10L))
                .willReturn(List.of(demoted));

        BusinessException ex =
                assertThrows(BusinessException.class, () -> service.deleteTeam(10L, 1L));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.NOT_TEAM_LEADER);
        assertThat(team.isDeleted()).isFalse();
        assertThat(demoted.getLeftAt()).isNull();
        verifyNoInteractions(chatMembershipService, teamEventPublisher, applicationEventPublisher);
    }

    private static TeamRepository.ActiveTeamMeta meta(Long id, Long postId) {
        return new TeamRepository.ActiveTeamMeta() {
            @Override
            public Long getId() {
                return id;
            }

            @Override
            public Long getPostId() {
                return postId;
            }
        };
    }

    private static Team team(Long id, Long postId, String name) {
        Team team = Team.create(postId, name);
        ReflectionTestUtils.setField(team, "id", id);
        return team;
    }
}
