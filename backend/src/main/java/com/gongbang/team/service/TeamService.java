package com.gongbang.team.service;

import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.team.domain.Team;
import com.gongbang.team.domain.TeamMember;
import com.gongbang.team.repository.TeamMemberRepository;
import com.gongbang.team.repository.TeamRepository;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 팀 만들기·팀원 추가 (WS-01). 다른 도메인(모집글·지원 수락 등)은 repository 대신 이 service를 부른다. */
@Service
@RequiredArgsConstructor
public class TeamService {

    /** 팀 이름 1~20자 (AGENTS.md 5). */
    static final int TEAM_NAME_MAX_LENGTH = 20;

    /** 팀원 고유 색 1~8 (WS-03). */
    static final int MAX_COLOR_INDEX = 8;

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;

    /**
     * 팀과 팀장 행(LEADER, 색 1)을 만들고 팀 ID를 돌려준다. 이름이 1~20자가 아니면 400 TEAM_NAME_INVALID.
     *
     * <p>TODO(확인 필요): 모집글 제목(최대 50자)이 20자를 넘을 때 어떤 이름을 넘길지 김수형 님과 협의 필요.
     */
    @Transactional
    public Long createTeam(Long postId, String name, Long leaderMemberId) {
        validateName(name);
        if (teamRepository.existsByPostId(postId)) {
            // TODO(확인 필요): 이미 팀이 있는 모집글. 문서에 에러 코드가 없어 일단 서버 오류로 둔다. post_id UQ가 마지막 방어선.
            throw new IllegalStateException("이미 팀이 있는 모집글입니다: postId=" + postId);
        }

        Team team = teamRepository.save(Team.create(postId, name));
        teamMemberRepository.save(TeamMember.leader(team, leaderMemberId));
        return team.getId();
    }

    /**
     * 팀원 행을 새로 만든다. 색은 현재 팀원이 쓰지 않는 1~8 중 가장 작은 번호. 이미 현재 팀원이면 409 ALREADY_TEAM_MEMBER.
     *
     * <p>부르는 쪽(지원자 수락 REC-004 등)이 recruit_post 행을 잠근 트랜잭션 안에서 부른다는 전제다 (AGENTS.md 7). 여기서도 팀 행을 잠가
     * 색 번호가 겹치지 않게 한다. 잠금 순서는 항상 recruit_post → team.
     */
    @Transactional
    public void addMember(Long teamId, Long memberId) {
        Team team =
                teamRepository
                        .findByIdForUpdate(teamId)
                        // TODO(확인 필요): 없는 팀. 문서에 TEAM_NOT_FOUND 같은 에러 코드가 없음.
                        .orElseThrow(() -> new IllegalStateException("없는 팀입니다: teamId=" + teamId));

        if (teamMemberRepository.existsByTeamIdAndMemberIdAndLeftAtIsNull(teamId, memberId)) {
            throw new BusinessException(ErrorCode.ALREADY_TEAM_MEMBER);
        }

        int colorIndex = smallestFreeColorIndex(teamId);
        teamMemberRepository.save(TeamMember.member(team, memberId, colorIndex));
    }

    @Transactional(readOnly = true)
    public Optional<Long> findTeamIdByPostId(Long postId) {
        return teamRepository.findByPostId(postId).map(Team::getId);
    }

    /** 팀 이름을 조회한다. 팀이 없으면 서버 상태 오류를 던진다. */
    @Transactional(readOnly = true)
    public String getTeamName(Long teamId) {
        return teamRepository
                .findById(teamId)
                .map(Team::getName)
                .orElseThrow(() -> new IllegalStateException("없는 팀입니다: teamId=" + teamId));
    }

    /** 팀의 현재 활성 팀원 ID 목록을 조회한다. */
    @Transactional(readOnly = true)
    public java.util.List<Long> findActiveMemberIds(Long teamId) {
        return teamMemberRepository
                .findAllByTeamIdAndLeftAtIsNullOrderByJoinedAtAscIdAsc(teamId)
                .stream()
                .map(TeamMember::getMemberId)
                .toList();
    }

    /** 팀 최근 활동 시각을 갱신한다. */
    @Transactional
    public void touchLastActivity(Long teamId, java.time.LocalDateTime activityAt) {
        teamRepository.findById(teamId).ifPresent(team -> team.touchLastActivity(activityAt));
    }

    // TODO(확인 필요): 앞뒤 공백을 지우고 셀지 문서에서 확인 필요. 지금은 받은 그대로 센다(공백만 있으면 오류).
    static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new BusinessException(ErrorCode.TEAM_NAME_INVALID);
        }
        // DB VARCHAR(20)은 문자(code point) 수로 센다.
        int length = name.codePointCount(0, name.length());
        if (length > TEAM_NAME_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.TEAM_NAME_INVALID);
        }
    }

    private int smallestFreeColorIndex(Long teamId) {
        Set<Integer> used = new HashSet<>(teamMemberRepository.findActiveColorIndexes(teamId));
        for (int colorIndex = 1; colorIndex <= MAX_COLOR_INDEX; colorIndex++) {
            if (!used.contains(colorIndex)) {
                return colorIndex;
            }
        }
        // TODO(확인 필요): 8색이 모두 쓰임. 목표 인원이 최대 8명이라 정상 흐름에서는 생기지 않는다. 에러 코드 문서에 없음.
        throw new IllegalStateException("남은 팀원 색이 없습니다: teamId=" + teamId);
    }
}
