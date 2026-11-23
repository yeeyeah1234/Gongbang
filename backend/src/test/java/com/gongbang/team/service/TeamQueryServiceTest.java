package com.gongbang.team.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.team.dto.WorkspaceSummaryResponse;
import com.gongbang.team.repository.TeamRepository;
import com.gongbang.team.repository.TeamWorkspaceSummary;
import java.math.BigInteger;
import java.sql.Date;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TeamQueryServiceTest {

    private static final Long TEAM_ID = 1L;
    private static final Long MEMBER_ID = 100L;

    @Mock private TeamRepository teamRepository;

    @Mock private TeamMemberService teamMemberService;

    private TeamQueryService teamQueryService;

    @BeforeEach
    void setUp() {
        teamQueryService = new TeamQueryService(teamRepository, teamMemberService);
    }

    private static TeamWorkspaceSummary summary(
            String teamName, String contestTitle, Object deadline) {
        return summary(teamName, null, contestTitle, deadline);
    }

    private static TeamWorkspaceSummary summary(
            String teamName, Number contestId, String contestTitle, Object deadline) {
        return new TeamWorkspaceSummary() {
            @Override
            public String getTeamName() {
                return teamName;
            }

            @Override
            public Number getContestId() {
                return contestId;
            }

            @Override
            public String getContestTitle() {
                return contestTitle;
            }

            @Override
            public Object getDeadline() {
                return deadline;
            }
        };
    }

    @Test
    @DisplayName("팀원이 아니면 403 NOT_TEAM_MEMBER이고 팀 정보를 읽지 않는다")
    void 팀원이_아니면_403이고_팀_정보를_읽지_않는다() {
        willThrow(new BusinessException(ErrorCode.NOT_TEAM_MEMBER))
                .given(teamMemberService)
                .requireMember(TEAM_ID, MEMBER_ID);

        BusinessException e =
                assertThrows(
                        BusinessException.class,
                        () -> teamQueryService.getWorkspaceSummary(TEAM_ID, MEMBER_ID));

        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NOT_TEAM_MEMBER);
        verify(teamRepository, never()).findWorkspaceSummary(TEAM_ID);
    }

    @Test
    @DisplayName("팀장이면 팀 이름·공모전 ID·공모전명·마감일·isLeader true·내 memberId를 돌려준다")
    void 팀장이면_요약과_isLeader_true를_돌려준다() {
        given(teamRepository.findWorkspaceSummary(TEAM_ID))
                .willReturn(
                        Optional.of(
                                summary(
                                        "공방팀",
                                        BigInteger.valueOf(55L),
                                        "공공데이터 공모전",
                                        LocalDate.of(2026, 12, 1))));
        given(teamMemberService.isLeader(TEAM_ID, MEMBER_ID)).willReturn(true);

        WorkspaceSummaryResponse response =
                teamQueryService.getWorkspaceSummary(TEAM_ID, MEMBER_ID);

        assertThat(response)
                .isEqualTo(
                        new WorkspaceSummaryResponse(
                                TEAM_ID,
                                "공방팀",
                                55L,
                                "공공데이터 공모전",
                                LocalDate.of(2026, 12, 1),
                                true,
                                MEMBER_ID));
    }

    @Test
    @DisplayName("마감일이 java.sql.Date로 와도 LocalDate로 바꾸고, 팀원이면 isLeader false")
    void 마감일이_sql_Date여도_LocalDate로_바꾼다() {
        given(teamRepository.findWorkspaceSummary(TEAM_ID))
                .willReturn(
                        Optional.of(
                                summary("공방팀", "외부 공모전", Date.valueOf(LocalDate.of(2026, 12, 5)))));
        given(teamMemberService.isLeader(TEAM_ID, MEMBER_ID)).willReturn(false);

        WorkspaceSummaryResponse response =
                teamQueryService.getWorkspaceSummary(TEAM_ID, MEMBER_ID);

        assertThat(response.deadline()).isEqualTo(LocalDate.of(2026, 12, 5));
        assertThat(response.isLeader()).isFalse();
    }

    @Test
    @DisplayName("모집글이 없어 공모전명·마감일이 없으면 null로 돌려준다")
    void 공모전명과_마감일이_없으면_null() {
        given(teamRepository.findWorkspaceSummary(TEAM_ID))
                .willReturn(Optional.of(summary("공방팀", null, null)));

        WorkspaceSummaryResponse response =
                teamQueryService.getWorkspaceSummary(TEAM_ID, MEMBER_ID);

        assertThat(response.teamName()).isEqualTo("공방팀");
        assertThat(response.contestId()).isNull();
        assertThat(response.contestTitle()).isNull();
        assertThat(response.deadline()).isNull();
    }
}
