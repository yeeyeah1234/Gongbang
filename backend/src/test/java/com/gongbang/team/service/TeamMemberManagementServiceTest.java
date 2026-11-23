package com.gongbang.team.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

import com.gongbang.chat.service.ChatMembershipService;
import com.gongbang.chat.service.ChatRealtimeService;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.global.websocket.TeamEventPublisher;
import com.gongbang.global.websocket.UserEventPublisher;
import com.gongbang.member.dto.MemberProfileSummary;
import com.gongbang.member.service.MemberService;
import com.gongbang.post.service.PostCommandService;
import com.gongbang.team.domain.LeaveType;
import com.gongbang.team.domain.Team;
import com.gongbang.team.domain.TeamMember;
import com.gongbang.team.domain.TeamRole;
import com.gongbang.team.dto.KickedEventPayload;
import com.gongbang.team.dto.TeamMemberKickResponse;
import com.gongbang.team.dto.TeamMemberResponse;
import com.gongbang.team.repository.TeamMemberRepository;
import com.gongbang.team.repository.TeamRepository;
import java.util.List;
import java.util.Map;
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
class TeamMemberManagementServiceTest {

    @Mock private TeamRepository teamRepository;
    @Mock private TeamMemberRepository teamMemberRepository;
    @Mock private TeamMemberService teamMemberService;
    @Mock private MemberService memberService;
    @Mock private PostCommandService postCommandService;
    @Mock private ChatMembershipService chatMembershipService;
    @Mock private ChatRealtimeService chatRealtimeService;
    @Mock private TeamEventPublisher teamEventPublisher;
    @Mock private UserEventPublisher userEventPublisher;
    @Mock private ApplicationEventPublisher applicationEventPublisher;

    private TeamMemberManagementService service;

    @BeforeEach
    void setUp() {
        service =
                new TeamMemberManagementService(
                        teamRepository,
                        teamMemberRepository,
                        teamMemberService,
                        memberService,
                        postCommandService,
                        chatMembershipService,
                        chatRealtimeService,
                        teamEventPublisher,
                        userEventPublisher,
                        applicationEventPublisher);
    }

    @Test
    @DisplayName("팀원 목록은 본인 맨 위 → 팀장 → 가입순으로 정렬해 반환한다")
    void 팀원목록_정렬() {
        Team team = Team.create(100L, "공방");
        ReflectionTestUtils.setField(team, "id", 10L);
        TeamMember leader = TeamMember.leader(team, 1L);
        TeamMember other = TeamMember.member(team, 2L, 2);
        TeamMember me = TeamMember.member(team, 3L, 3);

        given(teamMemberRepository.findAllByTeamIdAndLeftAtIsNullOrderByJoinedAtAscIdAsc(10L))
                .willReturn(List.of(leader, other, me));
        given(memberService.getProfileSummaries(anyCollection()))
                .willReturn(
                        Map.of(
                                1L, new MemberProfileSummary(1L, "팀장", null, List.of("PM")),
                                2L, new MemberProfileSummary(2L, "둘째", null, List.of("DESIGN")),
                                3L, new MemberProfileSummary(3L, "나", "/me.png", List.of("DEV"))));

        List<TeamMemberResponse> result = service.getMembers(10L, 3L);

        verify(teamMemberService).requireMember(10L, 3L);
        assertThat(result).extracting(TeamMemberResponse::memberId).containsExactly(3L, 1L, 2L);
        assertThat(result.get(0).desiredRoles()).containsExactly("DEV");
    }

    @Test
    @DisplayName("강제 퇴장은 recruit_post 잠금 감소 후 team 잠금 순서를 지키고 채팅·이벤트·Sync 종료를 처리한다")
    void 강제퇴장_정상() {
        Team team = Team.create(100L, "공방팀");
        ReflectionTestUtils.setField(team, "id", 10L);
        TeamMember target = TeamMember.member(team, 2L, 2);

        given(teamRepository.findById(10L)).willReturn(Optional.of(team));
        given(postCommandService.decreaseCurrentCount(100L)).willReturn(3);
        given(teamRepository.findByIdForUpdate(10L)).willReturn(Optional.of(team));
        given(teamMemberRepository.findByTeamIdAndMemberIdAndLeftAtIsNull(10L, 2L))
                .willReturn(Optional.of(target));
        given(memberService.displayName(2L)).willReturn("상현");

        TeamMemberKickResponse response = service.kickMember(10L, 1L, 2L);

        assertThat(response.currentCount()).isEqualTo(3);
        assertThat(target.getLeftAt()).isNotNull();
        assertThat(target.getLeaveType()).isEqualTo(LeaveType.KICKED);

        InOrder order = inOrder(postCommandService, teamRepository);
        order.verify(postCommandService).decreaseCurrentCount(100L);
        order.verify(teamRepository).findByIdForUpdate(10L);

        verify(chatMembershipService).removeMemberFromTeamRooms(10L, 2L);
        verify(chatRealtimeService).sendSystemMessage(10L, "상현님이 팀에서 강제 퇴장되었습니다.");
        verify(teamEventPublisher).publish(10L, "MEMBER_KICKED", 2L, 1L);
        verify(userEventPublisher)
                .publish(eq(2L), eq("KICKED"), eq(new KickedEventPayload(10L, "공방팀")));
        verify(applicationEventPublisher).publishEvent(new TeamMemberKickedEvent(10L, 2L));
    }

    @Test
    @DisplayName("자기 자신을 강제 퇴장하려 하면 400 CANNOT_KICK_SELF가 발생한다")
    void 자기자신_강제퇴장_차단() {
        BusinessException ex =
                assertThrows(BusinessException.class, () -> service.kickMember(10L, 1L, 1L));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CANNOT_KICK_SELF);
    }

    @Test
    @DisplayName("팀장 위임 시 한 트랜잭션에서 두 행의 역할을 맞바꾸고 시스템 메시지와 팀 이벤트를 발행한다")
    void 팀장위임_정상() {
        Team team = Team.create(100L, "공방팀");
        ReflectionTestUtils.setField(team, "id", 10L);
        TeamMember currentLeader = TeamMember.leader(team, 1L);
        TeamMember nextLeader = TeamMember.member(team, 2L, 2);

        given(teamRepository.findByIdForUpdate(10L)).willReturn(Optional.of(team));
        given(teamMemberRepository.findByTeamIdAndMemberIdAndLeftAtIsNull(10L, 1L))
                .willReturn(Optional.of(currentLeader));
        given(teamMemberRepository.findByTeamIdAndMemberIdAndLeftAtIsNull(10L, 2L))
                .willReturn(Optional.of(nextLeader));
        given(memberService.displayName(2L)).willReturn("수형");

        service.delegateLeader(10L, 1L, 2L);

        verify(teamMemberService).requireLeader(10L, 1L);
        assertThat(currentLeader.getRole()).isEqualTo(TeamRole.MEMBER);
        assertThat(nextLeader.getRole()).isEqualTo(TeamRole.LEADER);
        verify(chatRealtimeService).sendSystemMessage(10L, "수형님이 새로운 팀장이 되었습니다.");
        verify(teamEventPublisher).publish(10L, "LEADER_CHANGED", 2L, 1L);
    }
}
