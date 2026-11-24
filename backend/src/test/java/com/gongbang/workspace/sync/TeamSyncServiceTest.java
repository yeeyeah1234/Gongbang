package com.gongbang.workspace.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.domain.Member;
import com.gongbang.member.service.MemberService;
import com.gongbang.team.domain.TeamRole;
import com.gongbang.team.dto.TeamMemberResponse;
import com.gongbang.team.service.TeamMemberManagementService;
import com.gongbang.team.service.TeamService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** WS-03 동기화 서버 팀 접근 확인 순서(401 → 404 → 403)와 leader·colorIndex 응답을 본다. */
@ExtendWith(MockitoExtension.class)
class TeamSyncServiceTest {

    private static final Long TEAM_ID = 3L;
    private static final Long MEMBER_ID = 7L;

    @Mock private TeamService teamService;
    @Mock private TeamMemberManagementService teamMemberManagementService;
    @Mock private MemberService memberService;

    private TeamSyncService teamSyncService;

    @BeforeEach
    void setUp() {
        teamSyncService =
                new TeamSyncService(teamService, teamMemberManagementService, memberService);
    }

    private Member givenActiveMember() {
        Member member = mock(Member.class);
        when(memberService.findActiveMember(MEMBER_ID)).thenReturn(Optional.of(member));
        return member;
    }

    private static void assertErrorCode(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("access: 팀장이면 leader=true와 colorIndex를 돌려준다")
    void access_성공_팀장() {
        when(givenActiveMember().getNickname()).thenReturn("상현");
        when(teamMemberManagementService.getMembers(TEAM_ID, MEMBER_ID))
                .thenReturn(
                        List.of(
                                new TeamMemberResponse(
                                        MEMBER_ID, "상현", null, TeamRole.LEADER, 1, List.of())));

        TeamAccessResponse response = teamSyncService.checkAccess(MEMBER_ID, TEAM_ID);

        assertThat(response).isEqualTo(new TeamAccessResponse(MEMBER_ID, "상현", TEAM_ID, true, 1));
        verify(teamService).requireExists(TEAM_ID);
    }

    @Test
    @DisplayName("access: 일반 팀원이면 leader=false와 본인의 colorIndex를 돌려준다")
    void access_성공_팀원() {
        when(givenActiveMember().getNickname()).thenReturn("규선");
        when(teamMemberManagementService.getMembers(TEAM_ID, MEMBER_ID))
                .thenReturn(
                        List.of(
                                new TeamMemberResponse(
                                        1L, "하늘", null, TeamRole.LEADER, 1, List.of()),
                                new TeamMemberResponse(
                                        MEMBER_ID, "규선", null, TeamRole.MEMBER, 4, List.of())));

        TeamAccessResponse response = teamSyncService.checkAccess(MEMBER_ID, TEAM_ID);

        assertThat(response).isEqualTo(new TeamAccessResponse(MEMBER_ID, "규선", TEAM_ID, false, 4));
    }

    @Test
    @DisplayName("access: 토큰이 없거나 틀려 회원을 모르면 401 UNAUTHORIZED, 팀을 조회하지 않는다")
    void access_토큰없음_401() {
        assertErrorCode(() -> teamSyncService.checkAccess(null, TEAM_ID), ErrorCode.UNAUTHORIZED);
        verifyNoInteractions(memberService, teamService, teamMemberManagementService);
    }

    @Test
    @DisplayName("access: 탈퇴·삭제된 회원이면 401 UNAUTHORIZED")
    void access_탈퇴회원_401() {
        when(memberService.findActiveMember(MEMBER_ID)).thenReturn(Optional.empty());

        assertErrorCode(
                () -> teamSyncService.checkAccess(MEMBER_ID, TEAM_ID), ErrorCode.UNAUTHORIZED);
        verifyNoInteractions(teamService, teamMemberManagementService);
    }

    @Test
    @DisplayName("access: 없는 팀이면 팀원 확인 전에 404 RESOURCE_NOT_FOUND")
    void access_팀없음_404() {
        givenActiveMember();
        doThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND))
                .when(teamService)
                .requireExists(TEAM_ID);

        assertErrorCode(
                () -> teamSyncService.checkAccess(MEMBER_ID, TEAM_ID),
                ErrorCode.RESOURCE_NOT_FOUND);
        verifyNoInteractions(teamMemberManagementService);
    }

    @Test
    @DisplayName("access: 현재 팀원이 아니면 403 NOT_TEAM_MEMBER")
    void access_팀원아님_403() {
        givenActiveMember();
        when(teamMemberManagementService.getMembers(TEAM_ID, MEMBER_ID))
                .thenThrow(new BusinessException(ErrorCode.NOT_TEAM_MEMBER));

        assertErrorCode(
                () -> teamSyncService.checkAccess(MEMBER_ID, TEAM_ID), ErrorCode.NOT_TEAM_MEMBER);
        verify(teamService).requireExists(TEAM_ID);
    }
}
