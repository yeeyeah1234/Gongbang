package com.gongbang.comment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 모집글(`post_id`)·커뮤니티 게시글(`board_post_id`) 공용 댓글·대댓글 엔티티 (`comment`). */
@Entity
@Table(name = "comment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Comment {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", updatable = false)
    private Long postId;

    @Column(name = "board_post_id", updatable = false)
    private Long boardPostId;

    @Column(name = "author_id", nullable = false, updatable = false)
    private Long authorId;

    @Column(name = "parent_id", updatable = false)
    private Long parentId;

    @Column(name = "content", nullable = false, length = 500)
    private String content;

    @Column(name = "is_secret", nullable = false)
    private boolean secret;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private Comment(
            Long postId,
            Long boardPostId,
            Long authorId,
            Long parentId,
            String content,
            boolean secret) {
        if ((postId == null) == (boardPostId == null)) {
            throw new IllegalArgumentException("모집글 ID와 커뮤니티 게시글 ID 중 하나만 있어야 합니다.");
        }
        this.postId = postId;
        this.boardPostId = boardPostId;
        this.authorId = authorId;
        this.parentId = parentId;
        this.content = content;
        this.secret = secret;
    }

    /** 팀원 모집글 댓글·대댓글을 생성한다. */
    public static Comment forPost(
            Long postId, Long authorId, Long parentId, String content, boolean secret) {
        return new Comment(postId, null, authorId, parentId, content, secret);
    }

    /** 커뮤니티 게시글 댓글·대댓글을 생성한다. */
    public static Comment forBoardPost(
            Long boardPostId, Long authorId, Long parentId, String content, boolean secret) {
        return new Comment(null, boardPostId, authorId, parentId, content, secret);
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isReply() {
        return parentId != null;
    }

    public boolean isWrittenBy(Long memberId) {
        return memberId != null && authorId.equals(memberId);
    }

    public boolean belongsToPost(Long targetPostId) {
        return postId != null && postId.equals(targetPostId);
    }

    public boolean belongsToBoardPost(Long targetBoardPostId) {
        return boardPostId != null && boardPostId.equals(targetBoardPostId);
    }

    public void update(String content, boolean secret, LocalDateTime now) {
        this.content = content;
        this.secret = secret;
        this.updatedAt = now;
    }

    public void delete(LocalDateTime now) {
        this.deletedAt = now;
        this.updatedAt = now;
    }

    /** 관리자 댓글 숨김 처리(ADM-03/04) 호환용. */
    public void hide(LocalDateTime now) {
        delete(now);
    }

    /** 관리자 신고·숨김 목록 표시 호환용. */
    public String visibleContent() {
        return deletedAt == null ? content : "관리자에 의해 숨김 처리된 댓글입니다.";
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now(KST);
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = this.createdAt;
        }
    }

    public void restoreModeratedContent(java.time.LocalDateTime expectedHiddenAt) {
        if (deletedAt == null
                || expectedHiddenAt == null
                || !deletedAt
                        .truncatedTo(java.time.temporal.ChronoUnit.SECONDS)
                        .equals(
                                expectedHiddenAt.truncatedTo(
                                        java.time.temporal.ChronoUnit.SECONDS)))
            throw new com.gongbang.global.error.exception.BusinessException(
                    com.gongbang.global.error.ErrorCode.INVALID_INPUT, "삭제 상태가 변경되어 자동 복원할 수 없어요");
        deletedAt = null;
    }
}
