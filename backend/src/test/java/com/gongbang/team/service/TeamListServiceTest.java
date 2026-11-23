package com.gongbang.team.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.global.websocket.TeamEventPublisher;
import com.gongbang.member.dto.MemberProfileSummary;
import com.gongbang.member.service.MemberService;
import com.gongbang.team.domain.Team;
import com.gongbang.team.domain.TeamMember;
import com.gongbang.team.dto.MyTeamResponse;
import com.gongbang.team.dto.TeamRenameResponse;
import com.gongbang.team.repository.MyTeamRow;
import com.gongbang.team.repository.TeamMemberRepository;
import com.gongbang.team.repository.TeamRepository;
import java.math.BigInteger;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TeamListServiceTest {

    private static final Long ME = 100L;
    private static final Long OTHER = 200L;
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 23);

    @Mock private TeamRepository teamRepository;
    @Mock private TeamMemberRepository teamMemberRepository;
    @Mock private TeamMemberService teamMemberService;
    @Mock private MemberService memberService;
    @Mock private TeamEventPublisher teamEventPublisher;

    private TeamListService service;

    @BeforeEach
    void setUp() {
        service =
                new TeamListService(
                        teamRepository,
                        teamMemberRepository,
                        teamMemberService,
                        memberService,
                        teamEventPublisher);
    }

    private static MyTeamRow row(
            long teamId,
            String name,
            String contestTitle,
            Object deadline,
            Object lastActivityAt,
            String myRole) {
        return new MyTeamRow() {
            @Override
            public Number getTeamId() {
                return BigInteger.valueOf(teamId);
            }

            @Override
            public String getTeamName() {
                return name;
            }

            @Override
            public String getContestTitle() {
                return contestTitle;
            }

            @Override
            public Object getDeadline() {
                return deadline;
            }

            @Override
            public Object getLastActivityAt() {
                return lastActivityAt;
            }

            @Override
            public String getMyRole() {
                return myRole;
            }
        };
    }

    private static Team team(long id, String name) {
        Team team = Team.create(id * 10, name);
        ReflectionTestUtils.setField(team, "id", id);
        return team;
    }

    private void noMembers() {
        given(
                        teamMemberRepository
                                .findAllByTeamIdInAndLeftAtIsNullOrderByJoinedAtAscIdAsc(
                                        anyCollection()))
                .willReturn(List.of());
        given(memberService.getProfileSummaries(anyCollection())).willReturn(Map.of());
    }

    @Test
    @DisplayName("참여한 팀이 없으면 빈 목록이고 팀원 조회를 하지 않는다")
    void 참여한_팀이_없으면_빈_목록() {
        given(teamRepository.findMyTeams(ME)).willReturn(List.of());

        assertThat(service.getMyTeams(ME, TODAY)).isEmpty();
        verify(teamMemberRepository, never())
                .findAllByTeamIdInAndLeftAtIsNullOrderByJoinedAtAscIdAsc(anyCollection());
    }

    @Test
    @DisplayName("진행 중이 먼저 최근 활동순, 종료는 뒤에 최근 활동순 (WS-08 ②)")
    void 진행_중이_먼저_최근_활동순() {
        given(teamRepository.findMyTeams(ME))
                .willReturn(
                        List.of(
                                row(
                                        1,
                                        "오래된팀",
                                        "A",
                                        Date.valueOf("2026-12-01"),
                                        Timestamp.valueOf("2026-11-01 10:00:00"),
                                        "MEMBER"),
                                row(
                                        2,
                                        "종료팀",
                                        "B",
                                        Date.valueOf("2026-11-22"),
                                        Timestamp.valueOf("2026-11-22 10:00:00"),
                                        "MEMBER"),
                                row(
                                        3,
                                        "최근팀",
                                        "C",
                                        Date.valueOf("2026-11-30"),
                                        Timestamp.valueOf("2026-11-20 10:00:00"),
                                        "LEADER"),
                                row(
                                        4,
                                        "종료오래",
                                        "D",
                                        Date.valueOf("2026-10-01"),
                                        Timestamp.valueOf("2026-09-01 10:00:00"),
                                        "MEMBER")));
        noMembers();

        List<MyTeamResponse> result = service.getMyTeams(ME, TODAY);

        assertThat(result).extracting(MyTeamResponse::teamId).containsExactly(3L, 1L, 2L, 4L);
        assertThat(result)
                .extracting(MyTeamResponse::isEnded)
                .containsExactly(false, false, true, true);
        assertThat(result.get(0).isLeader()).isTrue();
        assertThat(result.get(1).isLeader()).isFalse();
    }

    @Test
    @DisplayName("마감 당일은 D-0 진행 중, 어제 마감은 종료, 마감일이 없으면 dDay null 진행 중")
    void dDay와_종료_계산() {
        given(teamRepository.findMyTeams(ME))
                .willReturn(
                        List.of(
                                row(
                                        1,
                                        "당일",
                                        "A",
                                        LocalDate.of(2026, 11, 23),
                                        LocalDateTime.of(2026, 11, 23, 9, 0),
                                        "MEMBER"),
                                row(
                                        2,
                                        "어제",
                                        "B",
                                        LocalDate.of(2026, 11, 22),
                                        LocalDateTime.of(2026, 11, 22, 9, 0),
                                        "MEMBER"),
                                row(
                                        3,
                                        "없음",
                                        null,
                                        null,
                                        LocalDateTime.of(2026, 11, 21, 9, 0),
                                        "MEMBER")));
        noMembers();

        Map<Long, MyTeamResponse> byId =
                service.getMyTeams(ME, TODAY).stream()
                        .collect(Collectors.toMap(MyTeamResponse::teamId, r -> r));

        assertThat(byId.get(1L).dDay()).isZero();
        assertThat(byId.get(1L).isEnded()).isFalse();
        assertThat(byId.get(2L).dDay()).isEqualTo(-1);
        assertThat(byId.get(2L).isEnded()).isTrue();
        assertThat(byId.get(3L).dDay()).isNull();
        assertThat(byId.get(3L).deadline()).isNull();
        assertThat(byId.get(3L).isEnded()).isFalse();
    }

    @Test
    @DisplayName("팀원은 팀별로 묶고 닉네임·색·인원 수를 채운다")
    void 팀원_아바타와_인원() {
        Team team = team(1, "공방팀");
        given(teamRepository.findMyTeams(ME))
                .willReturn(
                        List.of(
                                row(
                                        1,
                                        "공방팀",
                                        "A",
                                        null,
                                        LocalDateTime.of(2026, 11, 23, 9, 0),
                                        "LEADER")));
        given(
                        teamMemberRepository
                                .findAllByTeamIdInAndLeftAtIsNullOrderByJoinedAtAscIdAsc(
                                        List.of(1L)))
                .willReturn(
                        List.of(TeamMember.leader(team, ME), TeamMember.member(team, OTHER, 2)));
        given(memberService.getProfileSummaries(anyCollection()))
                .willReturn(
                        Map.of(
                                ME,
                                new MemberProfileSummary(ME, "나", "/me.png", List.of()),
                                OTHER,
                                new MemberProfileSummary(OTHER, "탈퇴한 사용자", null, List.of())));

        MyTeamResponse response = service.getMyTeams(ME, TODAY).get(0);

        assertThat(response.memberCount()).isEqualTo(2);
        assertThat(response.members())
                .containsExactly(
                        new MyTeamResponse.Member(ME, "나", "/me.png", 1),
                        new MyTeamResponse.Member(OTHER, "탈퇴한 사용자", null, 2));
    }

    @Test
    @DisplayName("팀장이 아니면 팀 이름을 바꿀 수 없다 (403 NOT_TEAM_LEADER)")
    void 팀장이_아니면_이름_변경_403() {
        willThrow(new BusinessException(ErrorCode.NOT_TEAM_LEADER))
                .given(teamMemberService)
                .requireLeader(1L, ME);

        BusinessException e =
                assertThrows(BusinessException.class, () -> service.renameTeam(1L, ME, "새이름"));

        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NOT_TEAM_LEADER);
        verify(teamRepository, never()).findById(anyLong());
        verify(teamEventPublisher, never()).publish(any(), anyString(), any(), any());
    }

    @Test
    @DisplayName("공백만·빈 값·null·21자는 400 TEAM_NAME_INVALID (WS-08 ⑦)")
    void 이름이_1에서_20자가_아니면_400() {
        for (String name : new String[] {"   ", "", null, "가".repeat(21)}) {
            BusinessException e =
                    assertThrows(BusinessException.class, () -> service.renameTeam(1L, ME, name));
            assertThat(e.getErrorCode()).isEqualTo(ErrorCode.TEAM_NAME_INVALID);
        }
        verify(teamRepository, never()).findById(anyLong());
        verify(teamEventPublisher, never()).publish(any(), anyString(), any(), any());
    }

    @Test
    @DisplayName("팀장이 20자 이름으로 바꾸면 저장하고 TEAM_RENAMED를 보낸다 (WS-08 ⑧)")
    void 팀장이_이름을_바꾸면_저장하고_이벤트() {
        Team team = team(1, "옛이름");
        given(teamRepository.findById(1L)).willReturn(Optional.of(team));
        String name = "가".repeat(20);

        TeamRenameResponse response = service.renameTeam(1L, ME, name);

        assertThat(response).isEqualTo(new TeamRenameResponse(1L, name));
        assertThat(team.getName()).isEqualTo(name);
        verify(teamEventPublisher).publish(1L, "TEAM_RENAMED", 1L, ME);
    }
}
