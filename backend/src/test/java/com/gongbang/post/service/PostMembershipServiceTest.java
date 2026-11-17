package com.gongbang.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.gongbang.post.service.PostMembershipService.Membership;
import com.gongbang.team.service.TeamMemberService;
import com.gongbang.team.service.TeamService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 모집글 팀에서 회원의 자리 (REC-001 ③, PST-006 ⑤⑨). */
@ExtendWith(MockitoExtension.class)
class PostMembershipServiceTest {

    private static final Long POST_ID = 10L;
    private static final Long TEAM_ID = 300L;
    private static final Long MEMBER_ID = 20L;

    @Mock private TeamService teamService;
    @Mock private TeamMemberService teamMemberService;

    private PostMembershipService postMembershipService;

    @BeforeEach
    void setUp() {
        postMembershipService = new PostMembershipService(teamService, teamMemberService);
    }

    @Test
    @DisplayName("팀이 아직 없는 글(첫 수락 전)이면 NONE, 팀원 테이블을 보지 않는다")
    void 팀이_없으면_NONE() {
        given(teamService.findTeamIdByPostId(POST_ID)).willReturn(Optional.empty());

        assertThat(postMembershipService.membership(POST_ID, MEMBER_ID)).isEqualTo(Membership.NONE);
        assertThat(postMembershipService.isActiveMember(POST_ID, MEMBER_ID)).isFalse();
        verifyNoInteractions(teamMemberService);
    }

    @Test
    @DisplayName("현재 팀원이면 ACTIVE, 나간 기록은 보지 않는다 (재초대로 다시 들어온 회원)")
    void 현재_팀원이면_ACTIVE() {
        given(teamService.findTeamIdByPostId(POST_ID)).willReturn(Optional.of(TEAM_ID));
        given(teamMemberService.isActiveMember(TEAM_ID, MEMBER_ID)).willReturn(true);

        assertThat(postMembershipService.membership(POST_ID, MEMBER_ID))
                .isEqualTo(Membership.ACTIVE);
        verify(teamMemberService, never()).hasLeft(TEAM_ID, MEMBER_ID);
    }

    @Test
    @DisplayName("현재 팀원이 아니고 나간 기록이 있으면 LEFT (강제 퇴장 포함)")
    void 나간_기록만_있으면_LEFT() {
        given(teamService.findTeamIdByPostId(POST_ID)).willReturn(Optional.of(TEAM_ID));
        given(teamMemberService.isActiveMember(TEAM_ID, MEMBER_ID)).willReturn(false);
        given(teamMemberService.hasLeft(TEAM_ID, MEMBER_ID)).willReturn(true);

        assertThat(postMembershipService.membership(POST_ID, MEMBER_ID)).isEqualTo(Membership.LEFT);
    }

    @Test
    @DisplayName("팀은 있지만 들어온 적이 없으면 NONE")
    void 들어온_적이_없으면_NONE() {
        given(teamService.findTeamIdByPostId(POST_ID)).willReturn(Optional.of(TEAM_ID));
        given(teamMemberService.isActiveMember(TEAM_ID, MEMBER_ID)).willReturn(false);
        given(teamMemberService.hasLeft(TEAM_ID, MEMBER_ID)).willReturn(false);

        assertThat(postMembershipService.membership(POST_ID, MEMBER_ID)).isEqualTo(Membership.NONE);
    }

    @Test
    @DisplayName("isActiveMember: 팀이 있으면 그 팀의 현재 팀원인지 본다")
    void 현재_팀원_여부() {
        given(teamService.findTeamIdByPostId(POST_ID)).willReturn(Optional.of(TEAM_ID));
        given(teamMemberService.isActiveMember(TEAM_ID, MEMBER_ID)).willReturn(true);

        assertThat(postMembershipService.isActiveMember(POST_ID, MEMBER_ID)).isTrue();
    }
}
