package com.gongbang.board.dto;

import com.gongbang.board.domain.BoardCategory;
import com.gongbang.board.domain.BoardPost;
import com.gongbang.contest.dto.ContestSummary;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** BRD-004 게시글 상세 (API 명세서 GET /api/board-posts/{boardPostId}). */
public record BoardPostDetailResponse(
        Long boardPostId,
        BoardCategory category,
        String title,
        String content,
        ContestInfo contest,
        String linkUrl,
        List<ImageInfo> images,
        AuthorInfo author,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        int viewCount,
        int likeCount,
        boolean isLiked,
        boolean isAuthor) {

    /** 공모전 카드. dDay는 마감 당일 0, 지나면 음수. */
    public record ContestInfo(
            Long contestId, String title, String categoryName, LocalDate deadline, int dDay) {

        public static ContestInfo of(ContestSummary contest, LocalDate today) {
            return new ContestInfo(
                    contest.contestId(),
                    contest.title(),
                    contest.categoryName(),
                    contest.deadline(),
                    (int) ChronoUnit.DAYS.between(today, contest.deadline()));
        }
    }

    /** BRD-006 결과물 사진 (결과물 자랑이 아니면 빈 목록). */
    public record ImageInfo(Long imageId, String url) {}

    /** 탈퇴한 회원은 nickname 「탈퇴한 사용자」, 사진 없음 (MY-11). */
    public record AuthorInfo(Long memberId, String nickname, String profileImageUrl) {}

    public static BoardPostDetailResponse of(
            BoardPost post,
            ContestInfo contest,
            List<ImageInfo> images,
            AuthorInfo author,
            int viewCount,
            boolean isLiked,
            boolean isAuthor) {
        return new BoardPostDetailResponse(
                post.getId(),
                post.getCategory(),
                post.getTitle(),
                post.getContent(),
                contest,
                post.getLinkUrl(),
                images,
                author,
                post.getCreatedAt(),
                post.getUpdatedAt(),
                viewCount,
                post.getLikeCount(),
                isLiked,
                isAuthor);
    }
}
