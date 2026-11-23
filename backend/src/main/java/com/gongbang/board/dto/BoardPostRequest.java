package com.gongbang.board.dto;

import com.gongbang.board.domain.BoardCategory;

/**
 * BRD-002 작성·BRD-003 수정 요청 (API 명세서 POST·PATCH /api/board-posts). 수정도 화면의 모든 값을 보낸다:
 * contestId·linkUrl을 보내지 않으면(또는 null·빈 문자열) 없는 값이 된다.
 */
public record BoardPostRequest(
        BoardCategory category, Long contestId, String title, String content, String linkUrl) {}
