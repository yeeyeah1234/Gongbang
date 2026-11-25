package com.gongbang.search.dto;

import com.gongbang.board.dto.BoardPostListItemResponse;
import com.gongbang.contest.dto.ContestCardResponse;
import com.gongbang.global.common.PageResponse;
import com.gongbang.post.dto.PostListItemResponse;

/**
 * GET /api/search 응답 (API 명세서 SRCH-02·05).
 *
 * @param posts 팀원 모집글. 항목은 PST-001 목록 항목과 같다
 * @param boardPosts 커뮤니티 게시글. 항목은 BRD-001 목록 항목과 같다
 */
public record SearchResponse(
        PageResponse<ContestCardResponse> contests,
        PageResponse<PostListItemResponse> posts,
        PageResponse<BoardPostListItemResponse> boardPosts) {}
