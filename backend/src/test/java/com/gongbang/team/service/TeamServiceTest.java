package com.gongbang.team.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TeamServiceTest {

    private static final Long POST_ID = 10L;
    private static final Long TEAM_ID = 1L;
    private static final Long LEADER_ID = 100L;

    @Mock private TeamRepository teamRepository;

    @Mock private TeamMemberRepository teamMemberRepository;

    private TeamService teamService;

    @BeforeEach
    void setUp() {
        teamService = new TeamService(teamRepository, teamMemberRepository);
    }

    private Team savedTeam() {
        Team team = Team.create(POST_ID, "공방팀");
        ReflectionTestUtils.setField(team, "id", TEAM_ID);
        return team;
    }

    @Test
    @DisplayName("createTeam하면 팀 행과 팀장 행(LEADER, 색 1)이 생기고 팀 ID를 돌려준다")
    void createTeam하면_팀장_행이_LEADER_색1로_생긴다() {
        given(teamRepository.existsByPostId(POST_ID)).willReturn(false);
        given(teamRepository.save(any(Team.class))).willReturn(savedTeam());

        Long teamId = teamService.createTeam(POST_ID, "공방팀", LEADER_ID);

        assertThat(teamId).isEqualTo(TEAM_ID);
        ArgumentCaptor<TeamMember> captor = ArgumentCaptor.forClass(TeamMember.class);
        verify(teamMemberRepository).save(captor.capture());
        TeamMember leader = captor.getValue();
        assertThat(leader.getMemberId()).isEqualTo(LEADER_ID);
        assertThat(leader.getRole()).isEqualTo(TeamRole.LEADER);
        assertThat(leader.getColorIndex()).isEqualTo(1);
        assertThat(leader.getTeam().getId()).isEqualTo(TEAM_ID);
        assertThat(leader.getLeftAt()).isNull();
    }

    @Test
    @DisplayName("팀 이름이 20자를 넘으면 400 TEAM_NAME_INVALID, 20자는 통과한다")
    void 팀_이름이_20자를_넘으면_createTeam_400() {
        String twentyOne = "가".repeat(21);

        BusinessException e =
                assertThrows(
                        BusinessException.class,
                        () -> teamService.createTeam(POST_ID, twentyOne, LEADER_ID));

        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.TEAM_NAME_INVALID);
        assertThat(e.getErrorCode().getHttpStatus().value()).isEqualTo(400);
        verify(teamRepository, never()).save(any());

        given(teamRepository.existsByPostId(POST_ID)).willReturn(false);
        given(teamRepository.save(any(Team.class))).willReturn(savedTeam());
        assertThat(teamService.createTeam(POST_ID, "가".repeat(20), LEADER_ID)).isEqualTo(TEAM_ID);
    }

    @Test
    @DisplayName("팀 이름이 비어 있으면 400 TEAM_NAME_INVALID")
    void 팀_이름이_비어_있으면_createTeam_400() {
        BusinessException e =
                assertThrows(
                        BusinessException.class,
                        () -> teamService.createTeam(POST_ID, " ", LEADER_ID));

        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.TEAM_NAME_INVALID);
    }

    @Test
    @DisplayName("addMember는 현재 팀원이 쓰지 않는 색 중 가장 작은 번호를 준다")
    void addMember는_빈_색_중_가장_작은_번호를_준다() {
        given(teamRepository.findByIdForUpdate(TEAM_ID)).willReturn(Optional.of(savedTeam()));
        given(teamMemberRepository.existsByTeamIdAndMemberIdAndLeftAtIsNull(TEAM_ID, 200L))
                .willReturn(false);
        given(teamMemberRepository.findActiveColorIndexes(TEAM_ID)).willReturn(List.of(1, 2, 4));

        teamService.addMember(TEAM_ID, 200L);

        ArgumentCaptor<TeamMember> captor = ArgumentCaptor.forClass(TeamMember.class);
        verify(teamMemberRepository).save(captor.capture());
        TeamMember member = captor.getValue();
        assertThat(member.getColorIndex()).isEqualTo(3);
        assertThat(member.getRole()).isEqualTo(TeamRole.MEMBER);
        assertThat(member.getMemberId()).isEqualTo(200L);
    }

    @Test
    @DisplayName("이미 현재 팀원이면 409 ALREADY_TEAM_MEMBER")
    void 이미_현재_팀원이면_addMember_409() {
        given(teamRepository.findByIdForUpdate(TEAM_ID)).willReturn(Optional.of(savedTeam()));
        given(teamMemberRepository.existsByTeamIdAndMemberIdAndLeftAtIsNull(TEAM_ID, 200L))
                .willReturn(true);

        BusinessException e =
                assertThrows(BusinessException.class, () -> teamService.addMember(TEAM_ID, 200L));

        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ALREADY_TEAM_MEMBER);
        assertThat(e.getErrorCode().getHttpStatus().value()).isEqualTo(409);
        verify(teamMemberRepository, never()).save(any());
    }
}
