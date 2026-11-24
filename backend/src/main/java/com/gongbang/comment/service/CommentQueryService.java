package com.gongbang.comment.service;

import com.gongbang.board.domain.BoardPost;
import com.gongbang.board.service.BoardPostQueryService;
import com.gongbang.comment.domain.Comment;
import com.gongbang.comment.dto.CommentResponse;
import com.gongbang.comment.repository.CommentRepository;
import com.gongbang.post.domain.RecruitPost;
import com.gongbang.post.service.PostQueryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** NET-01·BRD-005 댓글·대댓글 목록 조회 서비스 (비회원 조회 허용, 삭제된 게시글은 404). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentQueryService {

    private final CommentRepository commentRepository;
    private final PostQueryService postQueryService;
    private final BoardPostQueryService boardPostQueryService;
    private final CommentMapper commentMapper;

    /** 모집글 댓글·대댓글 트리를 조회한다 (`INT-004`). */
    public List<CommentResponse> getPostComments(Long postId, Long viewerId) {
        RecruitPost post = postQueryService.getActivePost(postId);
        List<Comment> comments = commentRepository.findByPostIdOrderByCreatedAtAscIdAsc(postId);
        return commentMapper.toTree(comments, post.getAuthorId(), viewerId);
    }

    /** 커뮤니티 게시글 댓글·대댓글 트리를 조회한다 (`BRD-005`). */
    public List<CommentResponse> getBoardPostComments(Long boardPostId, Long viewerId) {
        BoardPost post = boardPostQueryService.getActivePost(boardPostId);
        List<Comment> comments =
                commentRepository.findByBoardPostIdOrderByCreatedAtAscIdAsc(boardPostId);
        return commentMapper.toTree(comments, post.getAuthorId(), viewerId);
    }
}
