package com.gongbang.team.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gongbang.auth.domain.LoginMember;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.GlobalExceptionHandler;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.domain.MemberRole;
import com.gongbang.team.dto.TeamMemberKickResponse;
import com.gongbang.team.service.TeamLeaveService;
import com.gongbang.team.service.TeamListService;
import com.gongbang.team.service.TeamMemberManagementService;
import com.gongbang.team.service.TeamQueryService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 팀 나가기 API(WS-09) 경로·상태 코드 및 기존 강퇴 경로와의 라우팅 구분 확인. */
@ExtendWith(MockitoExtension.class)
class TeamControllerTest {

    private static final Long MEMBER_ID = 7L;

    @Mock private TeamQueryService teamQueryService;
    @Mock private TeamMemberManagementService teamMemberManagementService;
    @Mock private TeamListService teamListService;
    @Mock private TeamLeaveService teamLeaveService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(
                                new TeamController(
                                        teamQueryService,
                                        teamMemberManagementService,
                                        teamListService,
                                        teamLeaveService))
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                        .build();
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                new LoginMember(MEMBER_ID, MemberRole.USER), null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("팀 나가기: DELETE /api/teams/{teamId}/members/me 성공 시 200")
    void 팀_나가기_성공() throws Exception {
        mockMvc.perform(delete("/api/teams/10/members/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(teamLeaveService).leaveTeam(10L, MEMBER_ID);
    }

    @Test
    @DisplayName("팀 나가기: 팀장이면 409 LEADER_CANNOT_LEAVE")
    void 팀_나가기_팀장_거절() throws Exception {
        willThrow(new BusinessException(ErrorCode.LEADER_CANNOT_LEAVE))
                .given(teamLeaveService)
                .leaveTeam(10L, MEMBER_ID);

        mockMvc.perform(delete("/api/teams/10/members/me"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("LEADER_CANNOT_LEAVE"))
                .andExpect(jsonPath("$.error.message").value("팀장은 다른 팀원에게 팀장 권한을 넘긴 뒤 나갈 수 있어요"));
    }

    @Test
    @DisplayName("팀 나가기: 팀원이 아니면 403 NOT_TEAM_MEMBER")
    void 팀_나가기_비팀원() throws Exception {
        willThrow(new BusinessException(ErrorCode.NOT_TEAM_MEMBER))
                .given(teamLeaveService)
                .leaveTeam(10L, MEMBER_ID);

        mockMvc.perform(delete("/api/teams/10/members/me"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_TEAM_MEMBER"));
    }

    @Test
    @DisplayName("팀 나가기: 없는 팀이면 404 RESOURCE_NOT_FOUND")
    void 팀_나가기_없는_팀() throws Exception {
        willThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND))
                .given(teamLeaveService)
                .leaveTeam(999L, MEMBER_ID);

        mockMvc.perform(delete("/api/teams/999/members/me"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("기존 강퇴 경로 DELETE /api/teams/{teamId}/members/{memberId}와 충돌하지 않는다")
    void 강퇴_경로와_충돌하지_않는다() throws Exception {
        given(teamMemberManagementService.kickMember(10L, MEMBER_ID, 25L))
                .willReturn(new TeamMemberKickResponse(2));

        mockMvc.perform(delete("/api/teams/10/members/25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentCount").value(2));

        verify(teamMemberManagementService).kickMember(10L, MEMBER_ID, 25L);
    }
}
