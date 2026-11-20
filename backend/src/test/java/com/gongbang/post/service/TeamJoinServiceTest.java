package com.gongbang.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.gongbang.application.service.ApplicationCloseService;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.post.domain.PostStatus;
import com.gongbang.post.domain.RecruitPost;
import com.gongbang.post.dto.TeamJoinResult;
import com.gongbang.team.service.TeamService;
import com.gongbang.workspace.calendar.service.CalendarService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** INT-005 확정 팀원 등록 공통 처리 (TeamJoinService.join). */
@ExtendWith(MockitoExtension.class)
class TeamJoinServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 17, 20, 0);
    private static final Long POST_ID = 10L;
    private static final Long AUTHOR_ID = 7L;
    private static final Long MEMBER_ID = 20L;
    private static final Long TEAM_ID = 300L;

    @Mock private PostCommandService postCommandService;
    @Mock private TeamService teamService;
    @Mock private ApplicationCloseService applicationCloseService;
    @Mock private RecruitNotifier recruitNotifier;
    @Mock private CalendarService calendarService;

    private TeamJoinService teamJoinService;

    @BeforeEach
    void setUp() {
        teamJoinService =
                new TeamJoinService(
                        postCommandService,
                        teamService,
                        applicationCloseService,
                        recruitNotifier,
                        calendarService);
    }

    /** 목표 targetCount명, 확정 currentCount명인 글을 잠금 조회로 돌려준다. */
    private RecruitPost givenPost(String title, int currentCount, int targetCount) {
        LocalDate start = NOW.toLocalDate();
        RecruitPost post =
                RecruitPost.withContest(
                        AUTHOR_ID, 3L, title, "내용", targetCount, start, start.plusDays(7), null);
        ReflectionTestUtils.setField(post, "id", POST_ID);
        ReflectionTestUtils.setField(post, "currentCount", currentCount);
        given(postCommandService.lockPost(POST_ID)).willReturn(post);
        return post;
    }

    private ErrorCode errorOf() {
        return assertThrows(
                        BusinessException.class,
                        () -> teamJoinService.join(POST_ID, MEMBER_ID, NOW))
                .getErrorCode();
    }

    @Test
    @DisplayName("첫 확정이면 팀을 만들고(작성자가 팀장) 팀원으로 넣은 뒤 확정 인원을 1 올린다")
    void 첫_확정이면_팀을_만든다() {
        RecruitPost post = givenPost("AI 팀원 모집", 1, 4);
        given(teamService.findTeamIdByPostId(POST_ID)).willReturn(Optional.empty());
        given(teamService.createTeam(POST_ID, "AI 팀원 모집", AUTHOR_ID)).willReturn(TEAM_ID);

        TeamJoinResult result = teamJoinService.join(POST_ID, MEMBER_ID, NOW);

        verify(teamService).addMember(TEAM_ID, MEMBER_ID);
        // CAL-06: 팀을 만들 때 공모전 마감 자동 일정을 넣는다
        verify(calendarService).upsertAutoEvent(TEAM_ID);
        assertThat(result).isEqualTo(new TeamJoinResult(TEAM_ID, 2, 4, PostStatus.RECRUITING));
        assertThat(post.getCurrentCount()).isEqualTo(2);
        verifyNoInteractions(applicationCloseService);
    }

    @Test
    @DisplayName("이미 팀이 있으면 새로 만들지 않고 그 팀에 넣는다")
    void 팀이_있으면_그_팀에_넣는다() {
        givenPost("AI 팀원 모집", 2, 4);
        given(teamService.findTeamIdByPostId(POST_ID)).willReturn(Optional.of(TEAM_ID));

        TeamJoinResult result = teamJoinService.join(POST_ID, MEMBER_ID, NOW);

        verify(teamService, never()).createTeam(anyLong(), anyString(), anyLong());
        // 자동 일정은 팀을 만들 때 한 번만 넣는다
        verify(calendarService, never()).upsertAutoEvent(anyLong());
        verify(teamService).addMember(TEAM_ID, MEMBER_ID);
        assertThat(result.currentCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("팀 이름은 모집글 제목 앞 20자(이모지도 한 글자)로 만든다")
    void 팀_이름은_제목_앞_20자() {
        String title = "😀".repeat(5) + "가".repeat(30);
        givenPost(title, 1, 4);
        given(teamService.findTeamIdByPostId(POST_ID)).willReturn(Optional.empty());
        given(teamService.createTeam(POST_ID, "😀".repeat(5) + "가".repeat(15), AUTHOR_ID))
                .willReturn(TEAM_ID);

        assertThat(teamJoinService.join(POST_ID, MEMBER_ID, NOW).teamId()).isEqualTo(TEAM_ID);
    }

    @Test
    @DisplayName("확정 인원이 목표에 닿으면 자동 모집완료하고, 남은 대기 지원을 자동 거절하며 거절 알림을 보낸다")
    void 정원에_닿으면_자동_모집완료() {
        RecruitPost post = givenPost("AI 팀원 모집", 3, 4);
        given(teamService.findTeamIdByPostId(POST_ID)).willReturn(Optional.of(TEAM_ID));
        given(applicationCloseService.autoRejectPendingApplications(POST_ID, NOW))
                .willReturn(List.of(31L, 32L));

        TeamJoinResult result = teamJoinService.join(POST_ID, MEMBER_ID, NOW);

        assertThat(result).isEqualTo(new TeamJoinResult(TEAM_ID, 4, 4, PostStatus.COMPLETED));
        assertThat(post.getCompletedAt()).isEqualTo(NOW);
        verify(recruitNotifier).rejected(post, 31L, null);
        verify(recruitNotifier).rejected(post, 32L, null);
    }

    @Test
    @DisplayName("정원이 이미 찼으면 409 POST_FULL이고 팀원을 넣지 않는다")
    void 정원이_찼으면_POST_FULL() {
        givenPost("AI 팀원 모집", 4, 4);

        assertThat(errorOf()).isEqualTo(ErrorCode.POST_FULL);
        verify(teamService, never()).addMember(any(), any());
    }

    @Test
    @DisplayName("모집완료 글이면 409 POST_COMPLETED")
    void 모집완료_글이면_POST_COMPLETED() {
        RecruitPost post = givenPost("AI 팀원 모집", 2, 4);
        ReflectionTestUtils.setField(post, "status", PostStatus.COMPLETED);

        assertThat(errorOf()).isEqualTo(ErrorCode.POST_COMPLETED);
        verifyNoInteractions(teamService);
    }

    @Test
    @DisplayName("이미 현재 팀원이면 팀 서비스의 409 ALREADY_TEAM_MEMBER가 나오고 확정 인원은 그대로다")
    void 이미_팀원이면_ALREADY_TEAM_MEMBER() {
        RecruitPost post = givenPost("AI 팀원 모집", 2, 4);
        given(teamService.findTeamIdByPostId(POST_ID)).willReturn(Optional.of(TEAM_ID));
        willThrow(new BusinessException(ErrorCode.ALREADY_TEAM_MEMBER))
                .given(teamService)
                .addMember(TEAM_ID, MEMBER_ID);

        assertThat(errorOf()).isEqualTo(ErrorCode.ALREADY_TEAM_MEMBER);
        assertThat(post.getCurrentCount()).isEqualTo(2);
    }
}
