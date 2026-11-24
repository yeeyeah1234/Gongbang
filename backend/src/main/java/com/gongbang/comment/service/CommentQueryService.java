package com.gongbang.comment.service;

import com.gongbang.board.domain.BoardPost;
import com.gongbang.board.service.BoardPostQueryService;
import com.gongbang.comment.domain.Comment;
import com.gongbang.comment.dto.CommentResponse;
import com.gongbang.comment.repository.CommentRepository;
import com.gongbang.comment.repository.CommentRepository.CommentTargetCount;
import com.gongbang.post.domain.RecruitPost;
import com.gongbang.post.service.PostQueryService;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
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

    /**
     * 여러 모집글의 댓글 수를 한 번에 조회한다 (`MY-02` 등). 삭제·관리자 숨김되지 않은 댓글·답글·비공개 댓글(`deletedAt IS NULL`)만 센다.
     */
    public Map<Long, Long> countByPostIds(Collection<Long> postIds) {
        return countByTargetIds(postIds, commentRepository::countActiveByPostIds);
    }

    /**
     * 여러 커뮤니티 게시글의 댓글 수를 한 번에 조회한다 (`BRD-001`, `BRD-008`). 삭제·관리자 숨김되지 않은 댓글·답글·비공개 댓글(`deletedAt
     * IS NULL`)만 센다.
     */
    public Map<Long, Long> countByBoardPostIds(Collection<Long> boardPostIds) {
        return countByTargetIds(boardPostIds, commentRepository::countActiveByBoardPostIds);
    }

    private static Map<Long, Long> countByTargetIds(
            Collection<Long> targetIds,
            Function<Collection<Long>, List<CommentTargetCount>> loader) {
        if (targetIds == null || targetIds.isEmpty()) {
            return Map.of();
        }
        Set<Long> normalizedIds =
                targetIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (normalizedIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> counts = new LinkedHashMap<>();
        for (CommentTargetCount row : loader.apply(normalizedIds)) {
            counts.put(row.getTargetId(), row.getCount());
        }
        return counts;
    }
}
