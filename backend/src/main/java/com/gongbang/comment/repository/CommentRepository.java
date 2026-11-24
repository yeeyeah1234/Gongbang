package com.gongbang.comment.repository;

import com.gongbang.comment.domain.Comment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 댓글·대댓글 저장소. */
public interface CommentRepository extends JpaRepository<Comment, Long> {

    List<Comment> findByPostIdOrderByCreatedAtAscIdAsc(Long postId);

    List<Comment> findByPostIdOrderByIdAsc(Long postId);

    List<Comment> findByBoardPostIdOrderByCreatedAtAscIdAsc(Long boardPostId);

    List<Comment> findByParentIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(Long parentId);
}
