package com.gongbang.team.dto;

import java.time.LocalDate;

/**
 * WS-02 워크스페이스 헤더 정보. contestTitle·deadline은 모집글이나 값이 없으면 null. D-N은 화면이 오늘 날짜로 계산한다.
 *
 * <p>myMemberId는 탭 화면(채팅 등)이 내 메시지를 구분할 때 쓴다.
 */
public record WorkspaceSummaryResponse(
        Long teamId,
        String teamName,
        String contestTitle,
        LocalDate deadline,
        boolean isLeader,
        Long myMemberId) {}
