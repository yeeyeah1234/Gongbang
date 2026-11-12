package com.gongbang.search.dto;

import com.gongbang.contest.dto.ContestCardResponse;
import com.gongbang.global.common.PageResponse;

/**
 * GET /api/search 응답 (API 명세서 SRCH-02·05).
 *
 * @param posts 모집글 목록 항목. TODO(확인 필요): 모집글(PST-001, 김수형)·회원 닉네임(정진우)이 생기면 그 목록 항목 타입으로
 *     바꾸고 채운다. 지금은 항상 빈 목록이다.
 */
public record SearchResponse(
        PageResponse<ContestCardResponse> contests, PageResponse<Object> posts) {}
