package com.gongbang.board.service;

import com.gongbang.board.repository.BoardPostRepository;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.service.AdminMemberService;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** #148: 게시글 신고의 관리자 조회·숨김. 삭제된 원문도 관리자에게만 제공한다. */
@Service
@RequiredArgsConstructor
public class BoardPostModerationService {
    private final BoardPostRepository repository;
    private final AdminMemberService access;

    public record Target(Long authorId, String title, String content, LocalDateTime deletedAt) {}

    @Transactional(readOnly = true)
    public java.util.Map<Long, String> reportTitles(Long actor, java.util.Collection<Long> ids) {
        access.requireAdmin(actor);
        if (ids.isEmpty()) return java.util.Map.of();
        return repository.findAllById(ids).stream()
                .collect(
                        java.util.stream.Collectors.toMap(
                                com.gongbang.board.domain.BoardPost::getId,
                                com.gongbang.board.domain.BoardPost::getTitle));
    }

    @Transactional(readOnly = true)
    public Target target(Long actor, Long id) {
        access.requireAdmin(actor);
        var post =
                repository
                        .findById(id)
                        .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_POST_NOT_FOUND));
        return new Target(
                post.getAuthorId(), post.getTitle(), post.getContent(), post.getDeletedAt());
    }

    @Transactional
    public void hide(Long actor, Long id) {
        access.requireAdmin(actor);
        var post =
                repository
                        .findByIdForUpdate(id)
                        .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_POST_NOT_FOUND));
        if (!post.isDeleted()) post.delete(LocalDateTime.now(ZoneId.of("Asia/Seoul")));
    }
}
