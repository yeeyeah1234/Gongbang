package com.gongbang.board.dto;

import com.gongbang.board.domain.BoardCategory;
import com.gongbang.board.domain.BoardPost;
import java.time.LocalDateTime;

/** BRD-001 목록·BRD-008 인기글 항목 (API 명세서 GET /api/board-posts, /popular). */
public record BoardPostListItemResponse(
        Long boardPostId,
        BoardCategory category,
        String title,
        ContestInfo contest,
        AuthorInfo author,
        LocalDateTime createdAt,
        int viewCount,
        int likeCount) {

    /** 연결된 공모전 (없으면 null). */
    public record ContestInfo(Long contestId, String title) {}

    /** 탈퇴한 회원은 nickname이 「탈퇴한 사용자」 (MY-11). */
    public record AuthorInfo(Long memberId, String nickname) {}

    public static BoardPostListItemResponse of(
            BoardPost post, ContestInfo contest, AuthorInfo author) {
        return new BoardPostListItemResponse(
                post.getId(),
                post.getCategory(),
                post.getTitle(),
                contest,
                author,
                post.getCreatedAt(),
                post.getViewCount(),
                post.getLikeCount());
    }
}
