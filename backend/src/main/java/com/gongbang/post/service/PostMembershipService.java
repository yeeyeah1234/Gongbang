package com.gongbang.post.service;

import com.gongbang.team.service.TeamMemberService;
import com.gongbang.team.service.TeamService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 모집글 팀에서 회원의 자리. 팀원 여부의 기준은 team_member 하나다 (AGENTS.md 6). 팀은 첫 수락 때 생기므로, 팀이 없는 글은 모두 NONE이다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostMembershipService {

    private final TeamService teamService;
    private final TeamMemberService teamMemberService;

    public enum Membership {
        /** 현재 팀원 (left_at IS NULL). 재초대로 다시 들어온 회원도 여기. */
        ACTIVE,
        /** 현재 팀원이 아니고 나간 기록이 있음. 스스로 나감·강제 퇴장 모두 (PST-006 ⑨). */
        LEFT,
        NONE
    }

    /** isActiveMember를 먼저 보고, 아닐 때만 hasLeft를 본다 (11/13 결정). */
    public Membership membership(Long postId, Long memberId) {
        Optional<Long> teamId = teamService.findTeamIdByPostId(postId);
        if (teamId.isEmpty()) {
            return Membership.NONE;
        }
        if (teamMemberService.isActiveMember(teamId.get(), memberId)) {
            return Membership.ACTIVE;
        }
        if (teamMemberService.hasLeft(teamId.get(), memberId)) {
            return Membership.LEFT;
        }
        return Membership.NONE;
    }

    /** 이 글 팀의 현재 팀원인지 (초대로 들어온 회원 포함). */
    public boolean isActiveMember(Long postId, Long memberId) {
        return teamService
                .findTeamIdByPostId(postId)
                .map(teamId -> teamMemberService.isActiveMember(teamId, memberId))
                .orElse(false);
    }
}
