package com.gongbang.team.service;

import com.gongbang.global.websocket.TeamEventPublisher;
import com.gongbang.member.dto.MemberProfileSummary;
import com.gongbang.member.service.MemberService;
import com.gongbang.team.domain.Team;
import com.gongbang.team.domain.TeamMember;
import com.gongbang.team.domain.TeamRole;
import com.gongbang.team.dto.MyTeamResponse;
import com.gongbang.team.dto.TeamRenameResponse;
import com.gongbang.team.repository.MyTeamRow;
import com.gongbang.team.repository.TeamMemberRepository;
import com.gongbang.team.repository.TeamRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** WS-08 워크스페이스 팀 목록·팀 이름 변경. */
@Service
@RequiredArgsConstructor
public class TeamListService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /** 진행 중·종료 각 칸 안에서 최근 활동이 먼저 (WS-08 ②). */
    private static final Comparator<MyTeamResponse> RECENT_ACTIVITY_FIRST =
            Comparator.comparing(
                            MyTeamResponse::lastActivityAt,
                            Comparator.nullsLast(Comparator.<LocalDateTime>reverseOrder()))
                    .thenComparing(MyTeamResponse::teamId, Comparator.reverseOrder());

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamMemberService teamMemberService;
    private final MemberService memberService;
    private final TeamEventPublisher teamEventPublisher;

    /** 내가 현재 팀원인 팀. 진행 중 팀이 먼저, 종료된 팀이 뒤 (WS-08 ②). 참여한 팀이 없으면 빈 목록. */
    @Transactional(readOnly = true)
    public List<MyTeamResponse> getMyTeams(Long memberId) {
        return getMyTeams(memberId, LocalDate.now(KST));
    }

    List<MyTeamResponse> getMyTeams(Long memberId, LocalDate today) {
        List<MyTeamRow> rows = teamRepository.findMyTeams(memberId);
        if (rows.isEmpty()) {
            return List.of();
        }

        List<Long> teamIds = rows.stream().map(MyTeamRow::teamId).toList();
        Map<Long, List<TeamMember>> membersByTeam =
                teamMemberRepository
                        .findAllByTeamIdInAndLeftAtIsNullOrderByJoinedAtAscIdAsc(teamIds)
                        .stream()
                        .collect(Collectors.groupingBy(tm -> tm.getTeam().getId()));
        Map<Long, MemberProfileSummary> profiles =
                memberService.getProfileSummaries(
                        membersByTeam.values().stream()
                                .flatMap(List::stream)
                                .map(TeamMember::getMemberId)
                                .collect(Collectors.toSet()));

        Comparator<MyTeamResponse> order =
                Comparator.comparing(MyTeamResponse::isEnded).thenComparing(RECENT_ACTIVITY_FIRST);
        return rows.stream()
                .map(
                        row ->
                                toResponse(
                                        row,
                                        membersByTeam.getOrDefault(row.teamId(), List.of()),
                                        profiles,
                                        today))
                .sorted(order)
                .toList();
    }

    /**
     * 팀장만 팀 이름을 바꾼다 (WS-08 ⑥⑦). 팀장이 아니면 403 NOT_TEAM_LEADER, 1~20자가 아니거나 공백만이면 400
     * TEAM_NAME_INVALID. 팀 채팅방 이름은 team.name을 그대로 쓰므로 따로 고치지 않는다 (AGENTS.md 6). 커밋 후 팀원들에게
     * TEAM_RENAMED를 보낸다 (⑧).
     */
    @Transactional
    public TeamRenameResponse renameTeam(Long teamId, Long memberId, String name) {
        teamMemberService.requireLeader(teamId, memberId);
        TeamService.validateName(name);

        Team team =
                teamRepository
                        .findById(teamId)
                        // 현재 팀장이 있으면 팀 행도 있다 (team_member.team_id FK). 정상 흐름에서는 생기지 않는다.
                        .orElseThrow(() -> new IllegalStateException("없는 팀입니다: teamId=" + teamId));
        team.rename(name);
        teamEventPublisher.publish(teamId, "TEAM_RENAMED", teamId, memberId);
        return new TeamRenameResponse(teamId, team.getName());
    }

    private static MyTeamResponse toResponse(
            MyTeamRow row,
            List<TeamMember> teamMembers,
            Map<Long, MemberProfileSummary> profiles,
            LocalDate today) {
        LocalDate deadline = row.deadlineDate();
        Integer dDay = deadline == null ? null : (int) ChronoUnit.DAYS.between(today, deadline);
        // 마감일이 없으면 종료로 볼 근거가 없어 진행 중에 둔다
        boolean isEnded = deadline != null && deadline.isBefore(today);

        List<MyTeamResponse.Member> members =
                teamMembers.stream()
                        .map(
                                tm -> {
                                    MemberProfileSummary profile = profiles.get(tm.getMemberId());
                                    return new MyTeamResponse.Member(
                                            tm.getMemberId(),
                                            profile == null ? "탈퇴한 사용자" : profile.nickname(),
                                            profile == null ? null : profile.profileImageUrl(),
                                            tm.getColorIndex());
                                })
                        .toList();

        return new MyTeamResponse(
                row.teamId(),
                row.getTeamName(),
                row.getContestTitle(),
                deadline,
                dDay,
                isEnded,
                TeamRole.LEADER.name().equals(row.getMyRole()),
                members.size(),
                members,
                row.lastActivityAtValue());
    }
}
