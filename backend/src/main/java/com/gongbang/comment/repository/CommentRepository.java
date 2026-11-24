package com.gongbang.comment.repository;

import com.gongbang.comment.domain.Comment;
import com.gongbang.comment.dto.MyCommentItem;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 댓글·대댓글 저장소. */
public interface CommentRepository extends JpaRepository<Comment, Long> {
    @Query(
            value =
                    """
            SELECT c.id AS commentId, c.post_id AS postId, c.board_post_id AS boardPostId,
                   COALESCE(p.title, b.title) AS postTitle, c.content AS content, c.created_at AS createdAt
            FROM comment c
            LEFT JOIN recruit_post p ON p.id = c.post_id
            LEFT JOIN board_post b ON b.id = c.board_post_id
            WHERE c.author_id = :memberId AND c.deleted_at IS NULL
              AND ((p.id IS NOT NULL AND p.deleted_at IS NULL) OR (b.id IS NOT NULL AND b.deleted_at IS NULL))
            ORDER BY c.created_at DESC, c.id DESC
            """,
            countQuery =
                    """
            SELECT COUNT(*) FROM comment c
            LEFT JOIN recruit_post p ON p.id = c.post_id
            LEFT JOIN board_post b ON b.id = c.board_post_id
            WHERE c.author_id = :memberId AND c.deleted_at IS NULL
              AND ((p.id IS NOT NULL AND p.deleted_at IS NULL) OR (b.id IS NOT NULL AND b.deleted_at IS NULL))
            """,
            nativeQuery = true)
    Page<MyCommentItem> findMine(@Param("memberId") Long memberId, Pageable pageable);

    /** 게시글별 활성 댓글 수 집계 프로젝션. */
    interface CommentTargetCount {
        Long getTargetId();

        long getCount();
    }

    List<Comment> findByPostIdOrderByCreatedAtAscIdAsc(Long postId);

    List<Comment> findByPostIdOrderByIdAsc(Long postId);

    List<Comment> findByBoardPostIdOrderByCreatedAtAscIdAsc(Long boardPostId);

    List<Comment> findByParentIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(Long parentId);

    @Query(
            "select c.postId as targetId, count(c) as count "
                    + "from Comment c "
                    + "where c.postId in :postIds and c.deletedAt is null "
                    + "group by c.postId")
    List<CommentTargetCount> countActiveByPostIds(@Param("postIds") Collection<Long> postIds);

    @Query(
            "select c.boardPostId as targetId, count(c) as count "
                    + "from Comment c "
                    + "where c.boardPostId in :boardPostIds and c.deletedAt is null "
                    + "group by c.boardPostId")
    List<CommentTargetCount> countActiveByBoardPostIds(
            @Param("boardPostIds") Collection<Long> boardPostIds);
}
