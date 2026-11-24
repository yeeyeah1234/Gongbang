package com.gongbang.comment.service;

import com.gongbang.comment.domain.Comment;
import com.gongbang.member.service.MemberService;
import com.gongbang.notification.domain.NotificationType;
import com.gongbang.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** NET-01·BRD-005·NOTI-04 댓글·답글 작성 알림 발송기. */
@Component
@RequiredArgsConstructor
class CommentNotifier {

    static final String POST_TARGET_TYPE = "POST";
    static final String BOARD_POST_TARGET_TYPE = "BOARD_POST";

    private final NotificationService notificationService;
    private final MemberService memberService;

    record TargetInfo(
            String targetType, Long targetId, Long postAuthorId, String postTitle, String linkUrl) {

        static TargetInfo forPost(Long postId, Long authorId, String title) {
            return new TargetInfo(POST_TARGET_TYPE, postId, authorId, title, "/posts/" + postId);
        }

        static TargetInfo forBoardPost(Long boardPostId, Long authorId, String title) {
            return new TargetInfo(
                    BOARD_POST_TARGET_TYPE, boardPostId, authorId, title, "/board/" + boardPostId);
        }
    }

    /** 원댓글 작성 시 글 작성자에게 `COMMENT`, 답글 작성 시 원댓글 작성자에게 `REPLY`(및 다른 글 작성자에게 `COMMENT`)를 보낸다. */
    void notifyCreated(TargetInfo target, Comment parentOrNull, Long actorId) {
        String actorName = memberService.displayName(actorId);
        if (parentOrNull == null) {
            sendCommentNotification(target, actorId, actorName);
            return;
        }

        String replyMessage =
                String.format("‘%s’님이 ‘%s’ 댓글에 답글을 남겼어요", actorName, target.postTitle());
        notificationService.send(
                parentOrNull.getAuthorId(),
                NotificationType.REPLY,
                actorId,
                target.targetType(),
                target.targetId(),
                replyMessage,
                target.linkUrl());

        if (!target.postAuthorId().equals(parentOrNull.getAuthorId())) {
            sendCommentNotification(target, actorId, actorName);
        }
    }

    private void sendCommentNotification(TargetInfo target, Long actorId, String actorName) {
        String commentMessage =
                String.format("‘%s’님이 ‘%s’에 댓글을 남겼어요", actorName, target.postTitle());
        notificationService.send(
                target.postAuthorId(),
                NotificationType.COMMENT,
                actorId,
                target.targetType(),
                target.targetId(),
                commentMessage,
                target.linkUrl());
    }
}
