package com.gongbang.workspace.sync;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gongbang.auth.domain.LoginMember;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.GlobalExceptionHandler;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.domain.MemberRole;
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

/** WS-03 동기화 서버용 팀 권한 API의 경로·응답 필드·상태 코드 검증. */
@ExtendWith(MockitoExtension.class)
class InternalTeamControllerTest {

    private static final Long MEMBER_ID = 7L;
    private static final Long TEAM_ID = 3L;

    @Mock private TeamSyncService teamSyncService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(new InternalTeamController(teamSyncService))
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                        .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static void login() {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                new LoginMember(MEMBER_ID, MemberRole.USER), null, List.of()));
    }

    @Test
    @DisplayName("access: 토큰의 회원 ID를 넘기고 팀원 정보(leader·colorIndex 포함)를 돌려준다")
    void access_성공() throws Exception {
        login();
        given(teamSyncService.checkAccess(MEMBER_ID, TEAM_ID))
                .willReturn(new TeamAccessResponse(MEMBER_ID, "상현", TEAM_ID, true, 2));

        mockMvc.perform(get("/internal/teams/3/access"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.memberId").value(7))
                .andExpect(jsonPath("$.data.nickname").value("상현"))
                .andExpect(jsonPath("$.data.teamId").value(3))
                .andExpect(jsonPath("$.data.leader").value(true))
                .andExpect(jsonPath("$.data.colorIndex").value(2));
    }

    @Test
    @DisplayName("access: 토큰이 없으면 null을 넘기고 401 UNAUTHORIZED를 돌려준다")
    void access_토큰없음_401() throws Exception {
        given(teamSyncService.checkAccess(null, TEAM_ID))
                .willThrow(new BusinessException(ErrorCode.UNAUTHORIZED));

        mockMvc.perform(get("/internal/teams/3/access"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("access: 팀원이 아니면 403 NOT_TEAM_MEMBER")
    void access_팀원아님_403() throws Exception {
        login();
        given(teamSyncService.checkAccess(MEMBER_ID, TEAM_ID))
                .willThrow(new BusinessException(ErrorCode.NOT_TEAM_MEMBER));

        mockMvc.perform(get("/internal/teams/3/access"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_TEAM_MEMBER"));
    }

    @Test
    @DisplayName("access: 없는 팀이면 404 RESOURCE_NOT_FOUND")
    void access_팀없음_404() throws Exception {
        login();
        given(teamSyncService.checkAccess(MEMBER_ID, TEAM_ID))
                .willThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        mockMvc.perform(get("/internal/teams/3/access"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }
}
