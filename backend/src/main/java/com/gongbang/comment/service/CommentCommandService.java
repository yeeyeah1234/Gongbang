package com.gongbang.comment.service;

import com.gongbang.board.domain.BoardPost;
import com.gongbang.board.service.BoardPostQueryService;
import com.gongbang.comment.domain.Comment;
import com.gongbang.comment.dto.CommentCreateRequest;
import com.gongbang.comment.dto.CommentCreateResponse;
import com.gongbang.comment.dto.CommentResponse;
import com.gongbang.comment.dto.CommentUpdateRequest;
import com.gongbang.comment.repository.CommentRepository;
import com.gongbang.comment.service.CommentNotifier.TargetInfo;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.post.domain.RecruitPost;
import com.gongbang.post.service.PostQueryService;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** NET-01·BRD-005 댓글·대댓글 작성·수정·삭제 서비스. */
@Service
@RequiredArgsConstructor
@Transactional
public class CommentCommandService {

    static final int MAX_CONTENT_LENGTH = 500;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final CommentRepository commentRepository;
    private final PostQueryService postQueryService;
    private final BoardPostQueryService boardPostQueryService;
    private final CommentMapper commentMapper;
    private final CommentNotifier commentNotifier;

    /** 모집글에 댓글 또는 1단계 답글을 작성한다. */
    public CommentCreateResponse createPostComment(
            Long postId, Long authorId, CommentCreateRequest request) {
        RecruitPost post = postQueryService.getActivePost(postId);
        if (request == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "댓글 내용을 입력해 주세요");
        }
        String content = validateContent(request.content());
        boolean secret = Boolean.TRUE.equals(request.isSecret());
        Comment parent =
                resolveParentIfPresent(request.parentId(), c -> c.belongsToPost(post.getId()));

        Comment saved =
                commentRepository.save(
                        Comment.forPost(
                                post.getId(), authorId, request.parentId(), content, secret));
        commentNotifier.notifyCreated(
                TargetInfo.forPost(post.getId(), post.getAuthorId(), post.getTitle()),
                parent,
                authorId);
        return new CommentCreateResponse(saved.getId());
    }

    /** 커뮤니티 게시글에 댓글 또는 1단계 답글을 작성한다. */
    public CommentCreateResponse createBoardPostComment(
            Long boardPostId, Long authorId, CommentCreateRequest request) {
        BoardPost post = boardPostQueryService.getActivePost(boardPostId);
        if (request == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "댓글 내용을 입력해 주세요");
        }
        String content = validateContent(request.content());
        boolean secret = Boolean.TRUE.equals(request.isSecret());
        Comment parent =
                resolveParentIfPresent(request.parentId(), c -> c.belongsToBoardPost(post.getId()));

        Comment saved =
                commentRepository.save(
                        Comment.forBoardPost(
                                post.getId(), authorId, request.parentId(), content, secret));
        commentNotifier.notifyCreated(
                TargetInfo.forBoardPost(post.getId(), post.getAuthorId(), post.getTitle()),
                parent,
                authorId);
        return new CommentCreateResponse(saved.getId());
    }

    /** 본인 댓글·답글의 내용과 비공개 여부를 수정한다. */
    public CommentResponse updateComment(
            Long commentId, Long memberId, CommentUpdateRequest request) {
        Comment comment = getActiveComment(commentId);
        Long postAuthorId = resolveActivePostAuthorId(comment);
        requireCommentAuthor(comment, memberId);
        if (request == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "댓글 내용을 입력해 주세요");
        }

        String content = validateContent(request.content());
        boolean secret = Boolean.TRUE.equals(request.isSecret());
        comment.update(content, secret, LocalDateTime.now(KST));

        Comment parent =
                comment.isReply()
                        ? commentRepository.findById(comment.getParentId()).orElse(null)
                        : null;
        List<Comment> activeReplies =
                comment.isReply()
                        ? List.of()
                        : commentRepository
                                .findByParentIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(
                                        comment.getId());
        return commentMapper.toUpdatedResponse(
                comment, parent, activeReplies, postAuthorId, memberId);
    }

    /** 본인 댓글·답글을 소프트 삭제한다 (답글이 남은 원댓글은 조회 시 「삭제된 댓글입니다」로 표시). */
    public void deleteComment(Long commentId, Long memberId) {
        Comment comment = getActiveComment(commentId);
        resolveActivePostAuthorId(comment);
        requireCommentAuthor(comment, memberId);
        comment.delete(LocalDateTime.now(KST));
    }

    private Comment getActiveComment(Long commentId) {
        return commentRepository
                .findById(commentId)
                .filter(c -> !c.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
    }

    private Comment resolveParentIfPresent(Long parentId, Predicate<Comment> belongsToSameTarget) {
        if (parentId == null) {
            return null;
        }
        Comment parent =
                commentRepository
                        .findById(parentId)
                        .filter(c -> !c.isDeleted() && belongsToSameTarget.test(c))
                        .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
        if (parent.isReply()) {
            throw new BusinessException(ErrorCode.REPLY_DEPTH_EXCEEDED);
        }
        return parent;
    }

    private Long resolveActivePostAuthorId(Comment comment) {
        if (comment.getPostId() != null) {
            return postQueryService.getActivePost(comment.getPostId()).getAuthorId();
        }
        return boardPostQueryService.getActivePost(comment.getBoardPostId()).getAuthorId();
    }

    private static void requireCommentAuthor(Comment comment, Long memberId) {
        if (!comment.isWrittenBy(memberId)) {
            throw new BusinessException(ErrorCode.NOT_COMMENT_AUTHOR);
        }
    }

    private static String validateContent(String rawContent) {
        if (rawContent == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "댓글 내용을 입력해 주세요");
        }
        String trimmed = rawContent.trim();
        if (trimmed.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "댓글 내용을 입력해 주세요");
        }
        if (trimmed.length() > MAX_CONTENT_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "댓글은 500자까지 입력할 수 있어요");
        }
        return trimmed;
    }
}
