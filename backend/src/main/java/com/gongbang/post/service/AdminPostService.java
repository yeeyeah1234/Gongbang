package com.gongbang.post.service;

import com.gongbang.application.service.ApplicationCloseService;
import com.gongbang.global.error.*;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.invitation.service.InvitationCancelService;
import com.gongbang.member.service.AdminMemberService;
import com.gongbang.notification.domain.NotificationType;
import com.gongbang.notification.service.NotificationService;
import com.gongbang.post.domain.*;
import com.gongbang.post.repository.RecruitPostRepository;
import com.gongbang.report.domain.QReport;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminPostService {
    private final com.gongbang.admin.service.AdminActivityWriter audit;
    private final RecruitPostRepository repository;
    private final AdminMemberService access;
    private final ApplicationCloseService applications;
    private final NotificationService notifications;
    private final InvitationCancelService invitations;
    private final EntityManager em;

    public record Item(
            Long id,
            Long authorId,
            String title,
            String content,
            PostStatus status,
            LocalDateTime deletedAt,
            long reportCount) {}

    @Transactional(readOnly = true)
    public java.util.Map<Long, String> reportTitles(Long actor, java.util.Collection<Long> ids) {
        access.requireAdmin(actor);
        if (ids.isEmpty()) return java.util.Map.of();
        return repository.findAllById(ids).stream()
                .collect(
                        java.util.stream.Collectors.toMap(
                                com.gongbang.post.domain.RecruitPost::getId,
                                com.gongbang.post.domain.RecruitPost::getTitle));
    }

    @Transactional(readOnly = true)
    public Long reportAuthor(Long id) {
        return repository
                .findById(id)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND))
                .getAuthorId();
    }

    private Item item(RecruitPost p) {
        var r = QReport.report;
        Long count =
                new JPAQueryFactory(em)
                        .select(r.count())
                        .from(r)
                        .where(
                                r.targetType
                                        .stringValue()
                                        .eq("POST")
                                        .and(r.targetId.eq(p.getId()))
                                        .and(r.status.stringValue().eq("RECEIVED")))
                        .fetchOne();
        return new Item(
                p.getId(),
                p.getAuthorId(),
                p.getTitle(),
                p.getContent(),
                p.getStatus(),
                p.getDeletedAt(),
                count == null ? 0 : count);
    }

    @Transactional(readOnly = true)
    public Page<Item> list(Long actor, String keyword, boolean reportedFirst, int page) {
        access.requireAdmin(actor);
        if (page < 0 || keyword.length() > 100)
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        var p = QRecruitPost.recruitPost;
        var r = QReport.report;
        var q = new JPAQueryFactory(em);
        var where = p.title.containsIgnoreCase(keyword);
        var query = q.selectFrom(p).where(where);
        if (reportedFirst)
            query.orderBy(
                    new OrderSpecifier<>(
                            Order.DESC,
                            JPAExpressions.select(r.count())
                                    .from(r)
                                    .where(
                                            r.targetType.stringValue().eq("POST"),
                                            r.targetId.eq(p.id),
                                            r.status.stringValue().eq("RECEIVED"))));
        var rows = query.orderBy(p.id.desc()).offset((long) page * 20).limit(20).fetch();
        Long count = q.select(p.count()).from(p).where(where).fetchOne();
        return new PageImpl<>(
                rows.stream().map(this::item).toList(),
                PageRequest.of(page, 20),
                count == null ? 0 : count);
    }

    @Transactional(readOnly = true)
    public Item target(Long actor, Long id) {
        access.requireAdmin(actor);
        return item(
                repository
                        .findById(id)
                        .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND)));
    }

    @Transactional
    public void hide(Long actor, Long id) {
        access.requireAdmin(actor);
        var p =
                repository
                        .findByIdForUpdate(id)
                        .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        if (p.getDeletedAt() != null) return;
        var now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        p.forceHide(now);
        audit.record(actor, "POST_HIDE", "POST", id, null);
        invitations.cancelPendingByPostId(id, now);
        var receivers = applications.closePendingApplications(id, now);
        // 알림과 이메일 대기열은 변경과 함께 커밋된다. 실제 메일은 기존 outbox 작업이 전송한다.
        for (Long receiver : receivers)
            notifications.send(
                    receiver,
                    NotificationType.POST_DELETED,
                    null,
                    "POST",
                    id,
                    "지원한 모집글 '" + p.getTitle() + "'이 관리자에 의해 삭제되어 모집이 마감됐어요.",
                    "/notifications");
    }

    @Transactional
    public void restoreHidden(Long actor, Long id, LocalDateTime expectedHiddenAt) {
        access.requireAdmin(actor);
        var target =
                repository
                        .findByIdForUpdate(id)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (target == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        target.restoreModeratedContent(expectedHiddenAt);
        audit.record(actor, "POST_RESTORE", "POST", id, null);
    }

    @Transactional
    public LocalDateTime hideForReview(Long actor, Long id) {
        access.requireAdmin(actor);
        var target =
                repository
                        .findByIdForUpdate(id)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (target == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        if (target.getDeletedAt() != null) return null;
        hide(actor, id);
        return target.getDeletedAt();
    }
}
