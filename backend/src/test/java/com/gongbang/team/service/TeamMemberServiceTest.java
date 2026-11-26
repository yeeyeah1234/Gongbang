package com.gongbang.team.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.team.domain.TeamRole;
import com.gongbang.team.repository.TeamMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TeamMemberServiceTest {

    private static final Long TEAM_ID = 1L;
    private static final Long MEMBER_ID = 200L;

    @Mock private TeamMemberRepository teamMemberRepository;

    private TeamMemberService teamMemberService;

    @BeforeEach
    void setUp() {
        teamMemberService = new TeamMemberService(teamMemberRepository);
    }

    @Test
    @DisplayName("현재 팀원이 아니거나 삭제된 팀이면 requireMember는 403 NOT_TEAM_MEMBER")
    void 팀원이_아니면_requireMember_403() {
        given(
                        teamMemberRepository
                                .existsByTeamIdAndTeamDeletedAtIsNullAndMemberIdAndLeftAtIsNull(
                                        TEAM_ID, MEMBER_ID))
                .willReturn(false);

        BusinessException e =
                assertThrows(
                        BusinessException.class,
                        () -> teamMemberService.requireMember(TEAM_ID, MEMBER_ID));

        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NOT_TEAM_MEMBER);
        assertThat(e.getErrorCode().getHttpStatus().value()).isEqualTo(403);
    }

    @Test
    @DisplayName("현재 팀장이 아니거나 삭제된 팀이면 requireLeader는 403 NOT_TEAM_LEADER")
    void 팀장이_아니면_requireLeader_403() {
        given(
                        teamMemberRepository
                                .existsByTeamIdAndTeamDeletedAtIsNullAndMemberIdAndRoleAndLeftAtIsNull(
                                        TEAM_ID, MEMBER_ID, TeamRole.LEADER))
                .willReturn(false);

        BusinessException e =
                assertThrows(
                        BusinessException.class,
                        () -> teamMemberService.requireLeader(TEAM_ID, MEMBER_ID));

        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NOT_TEAM_LEADER);
        assertThat(e.getErrorCode().getHttpStatus().value()).isEqualTo(403);
    }

    @Test
    @DisplayName("삭제된 팀이면 isActiveMember와 isLeader는 false")
    void 삭제된_팀이면_false() {
        given(
                        teamMemberRepository
                                .existsByTeamIdAndTeamDeletedAtIsNullAndMemberIdAndLeftAtIsNull(
                                        TEAM_ID, MEMBER_ID))
                .willReturn(false);
        given(
                        teamMemberRepository
                                .existsByTeamIdAndTeamDeletedAtIsNullAndMemberIdAndRoleAndLeftAtIsNull(
                                        TEAM_ID, MEMBER_ID, TeamRole.LEADER))
                .willReturn(false);

        assertThat(teamMemberService.isActiveMember(TEAM_ID, MEMBER_ID)).isFalse();
        assertThat(teamMemberService.isLeader(TEAM_ID, MEMBER_ID)).isFalse();
    }
}
