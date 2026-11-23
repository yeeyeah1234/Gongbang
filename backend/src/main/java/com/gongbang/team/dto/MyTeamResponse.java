package com.gongbang.team.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * WS-08 워크스페이스 팀 목록 카드 한 장 (API 명세서 GET /api/teams).
 *
 * <ul>
 *   <li>contestTitle·deadline·dDay는 모집글이나 마감일이 없으면 null.
 *   <li>dDay = 마감일 - 오늘(KST). 마감일이 지나면 음수이고 isEnded = true.
 *   <li>isEnded는 저장하지 않고 마감일과 오늘 날짜로 계산한다 (AGENTS.md 6).
 * </ul>
 *
 * <p>TODO(확인 필요): API 명세서의 lastMessage·unreadCount는 채팅(김건우) 데이터라 이번에 넣지 않았다. 이상현과 협의해 나중에 붙인다.
 */
public record MyTeamResponse(
        Long teamId,
        String name,
        String contestTitle,
        LocalDate deadline,
        Integer dDay,
        boolean isEnded,
        boolean isLeader,
        int memberCount,
        List<Member> members,
        LocalDateTime lastActivityAt) {

    /** 카드 아바타용 팀원 (입장 순). */
    public record Member(Long memberId, String nickname, String profileImageUrl, int colorIndex) {}
}
