package com.gongbang.comment.service;

import com.gongbang.comment.domain.Comment;
import com.gongbang.comment.dto.CommentAuthorResponse;
import com.gongbang.comment.dto.CommentResponse;
import com.gongbang.member.dto.MemberProfileSummary;
import com.gongbang.member.service.MemberService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 댓글·대댓글 트리 조립 및 비공개·삭제 마스킹 매퍼. */
@Component
@RequiredArgsConstructor
class CommentMapper {

    private final MemberService memberService;

    /** 게시글의 전체 댓글 행을 1단계 트리 응답으로 변환한다 (답글 없는 삭제 원댓글 및 삭제된 답글 제외). */
    List<CommentResponse> toTree(List<Comment> comments, Long postAuthorId, Long viewerId) {
        if (comments.isEmpty()) {
            return List.of();
        }

        Map<Long, List<Comment>> activeRepliesByParent = new LinkedHashMap<>();
        List<Comment> roots = new ArrayList<>();
        for (Comment comment : comments) {
            if (comment.isReply()) {
                if (!comment.isDeleted()) {
                    activeRepliesByParent
                            .computeIfAbsent(comment.getParentId(), k -> new ArrayList<>())
                            .add(comment);
                }
            } else {
                roots.add(comment);
            }
        }

        Set<Long> authorIds = new LinkedHashSet<>();
        for (Comment root : roots) {
            List<Comment> replies = activeRepliesByParent.getOrDefault(root.getId(), List.of());
            if (root.isDeleted() && replies.isEmpty()) {
                continue;
            }
            if (!root.isDeleted()) {
                authorIds.add(root.getAuthorId());
            }
            for (Comment reply : replies) {
                authorIds.add(reply.getAuthorId());
            }
        }
        Map<Long, MemberProfileSummary> profiles = memberService.getProfileSummaries(authorIds);

        List<CommentResponse> result = new ArrayList<>();
        for (Comment root : roots) {
            List<Comment> replies = activeRepliesByParent.getOrDefault(root.getId(), List.of());
            if (root.isDeleted() && replies.isEmpty()) {
                continue;
            }
            List<CommentResponse> replyDtos =
                    replies.stream()
                            .map(
                                    reply ->
                                            toReplyResponse(
                                                    reply, root, postAuthorId, viewerId, profiles))
                            .toList();
            result.add(toRootResponse(root, replyDtos, postAuthorId, viewerId, profiles));
        }
        return result;
    }

    /** 단건 댓글 수정 직후 응답을 조립한다. */
    CommentResponse toUpdatedResponse(
            Comment comment,
            Comment parentOrNull,
            List<Comment> activeReplies,
            Long postAuthorId,
            Long viewerId) {
        Set<Long> authorIds = new LinkedHashSet<>();
        authorIds.add(comment.getAuthorId());
        for (Comment reply : activeReplies) {
            authorIds.add(reply.getAuthorId());
        }
        Map<Long, MemberProfileSummary> profiles = memberService.getProfileSummaries(authorIds);

        if (comment.isReply()) {
            return toReplyResponse(comment, parentOrNull, postAuthorId, viewerId, profiles);
        }
        List<CommentResponse> replyDtos =
                activeReplies.stream()
                        .map(
                                reply ->
                                        toReplyResponse(
                                                reply, comment, postAuthorId, viewerId, profiles))
                        .toList();
        return toRootResponse(comment, replyDtos, postAuthorId, viewerId, profiles);
    }

    private static CommentResponse toRootResponse(
            Comment root,
            List<CommentResponse> replyDtos,
            Long postAuthorId,
            Long viewerId,
            Map<Long, MemberProfileSummary> profiles) {
        if (root.isDeleted()) {
            return new CommentResponse(
                    root.getId(),
                    null,
                    false,
                    null,
                    false,
                    true,
                    root.getCreatedAt(),
                    root.getUpdatedAt(),
                    replyDtos);
        }
        boolean readable = !root.isSecret() || canReadRoot(root, postAuthorId, viewerId);
        return new CommentResponse(
                root.getId(),
                toAuthor(root.getAuthorId(), profiles),
                root.getAuthorId().equals(postAuthorId),
                readable ? root.getContent() : null,
                root.isSecret(),
                false,
                root.getCreatedAt(),
                root.getUpdatedAt(),
                replyDtos);
    }

    private static CommentResponse toReplyResponse(
            Comment reply,
            Comment parentOrNull,
            Long postAuthorId,
            Long viewerId,
            Map<Long, MemberProfileSummary> profiles) {
        boolean readable =
                !reply.isSecret() || canReadReply(reply, parentOrNull, postAuthorId, viewerId);
        return new CommentResponse(
                reply.getId(),
                toAuthor(reply.getAuthorId(), profiles),
                reply.getAuthorId().equals(postAuthorId),
                readable ? reply.getContent() : null,
                reply.isSecret(),
                false,
                reply.getCreatedAt(),
                reply.getUpdatedAt(),
                List.of());
    }

    private static boolean canReadRoot(Comment root, Long postAuthorId, Long viewerId) {
        return viewerId != null
                && (viewerId.equals(postAuthorId) || viewerId.equals(root.getAuthorId()));
    }

    private static boolean canReadReply(
            Comment reply, Comment parentOrNull, Long postAuthorId, Long viewerId) {
        if (viewerId == null) {
            return false;
        }
        if (viewerId.equals(postAuthorId) || viewerId.equals(reply.getAuthorId())) {
            return true;
        }
        return parentOrNull != null
                && !parentOrNull.isDeleted()
                && viewerId.equals(parentOrNull.getAuthorId());
    }

    private static CommentAuthorResponse toAuthor(
            Long authorId, Map<Long, MemberProfileSummary> profiles) {
        MemberProfileSummary summary = profiles.get(authorId);
        if (summary == null) {
            return new CommentAuthorResponse(authorId, "탈퇴한 사용자", null);
        }
        return new CommentAuthorResponse(authorId, summary.nickname(), summary.profileImageUrl());
    }
}
