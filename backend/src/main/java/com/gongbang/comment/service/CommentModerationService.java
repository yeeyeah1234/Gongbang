package com.gongbang.comment.service;

import com.gongbang.comment.domain.Comment;
import com.gongbang.comment.repository.CommentRepository;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.service.AdminMemberService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CommentModerationService {
    private final CommentRepository repository;
    private final AdminMemberService access;
    private final EntityManager em;

    public record Item(
            Long id, Long postId, Long authorId, String content, LocalDateTime deletedAt) {}

    @Transactional(readOnly = true)
    public Long reportAuthor(Long id) {
        return repository
                .findById(id)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND))
                .getAuthorId();
    }

    @Transactional(readOnly = true)
    public Item target(Long actor, Long id) {
        access.requireAdmin(actor);
        Comment c =
                repository
                        .findById(id)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        return new Item(
                c.getId(), c.getPostId(), c.getAuthorId(), c.getContent(), c.getDeletedAt());
    }

    @Transactional
    public void hide(Long actor, Long id) {
        access.requireAdmin(actor);
        Comment c = em.find(Comment.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (c == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        if (c.getDeletedAt() == null) c.hide(LocalDateTime.now(ZoneId.of("Asia/Seoul")));
    }

    @Transactional(readOnly = true)
    public List<Item> list(Long postId) {
        return repository.findByPostIdOrderByIdAsc(postId).stream()
                .map(
                        c ->
                                new Item(
                                        c.getId(),
                                        c.getPostId(),
                                        c.getAuthorId(),
                                        c.visibleContent(),
                                        c.getDeletedAt()))
                .toList();
    }
}
