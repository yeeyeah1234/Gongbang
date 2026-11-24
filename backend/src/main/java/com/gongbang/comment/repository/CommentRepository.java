package com.gongbang.comment.repository;

import com.gongbang.comment.domain.Comment;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 댓글·대댓글 저장소. */
public interface CommentRepository extends JpaRepository<Comment, Long> {

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
