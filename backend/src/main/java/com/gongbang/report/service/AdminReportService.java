package com.gongbang.report.service;

import com.gongbang.comment.service.CommentModerationService;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.service.*;
import com.gongbang.post.service.AdminPostService;
import com.gongbang.report.domain.*;
import com.gongbang.report.repository.ReportRepository;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.*;
import java.time.*;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminReportService {
    private final AdminMemberService access;
    private final AdminMemberManagementService members;
    private final MemberService memberLookup;
    private final AdminPostService posts;
    private final CommentModerationService comments;
    private final ReportRepository repository;
    private final EntityManager em;
    private final com.gongbang.chat.service.ChatReportService chat;

    public record Item(
            Long id,
            Long reporterId,
            String targetType,
            Long targetId,
            String reason,
            String status,
            String action,
            String reviewNote,
            Long reviewerId,
            LocalDateTime reviewedAt,
            LocalDateTime createdAt) {
        static Item of(Report r) {
            return new Item(
                    r.getId(),
                    r.getReporterId(),
                    r.getTargetType().name(),
                    r.getTargetId(),
                    r.getReasonCode().name() + (r.getDetail() == null ? "" : ": " + r.getDetail()),
                    r.getStatus().name(),
                    r.getAction(),
                    r.getReviewNote(),
                    r.getReviewerId(),
                    r.getReviewedAt(),
                    r.getCreatedAt());
        }
    }

    public record Review(String action, String reason, LocalDateTime until) {}

    public record Detail(Item report, Long memberId, String content) {}

    @Transactional(readOnly = true)
    public Page<Item> list(Long actor, String status, String type, int page) {
        access.requireAdmin(actor);
        if (page < 0
                || (!status.isEmpty()
                        && !Set.of("RECEIVED", "RESOLVED", "DISMISSED").contains(status)))
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        var r = QReport.report;
        var q = new JPAQueryFactory(em);
        var where = r.id.isNotNull();
        if (!status.isEmpty()) where = where.and(r.status.stringValue().eq(status));
        if (!type.isEmpty()) where = where.and(r.targetType.stringValue().eq(type));
        var rows =
                q.selectFrom(r)
                        .where(where)
                        .orderBy(r.createdAt.desc(), r.id.desc())
                        .offset((long) page * 20)
                        .limit(20)
                        .fetch();
        Long count = q.select(r.count()).from(r).where(where).fetchOne();
        return new PageImpl<>(
                rows.stream().map(Item::of).toList(),
                PageRequest.of(page, 20),
                count == null ? 0 : count);
    }

    @Transactional(readOnly = true)
    public Detail detail(Long actor, Long id) {
        access.requireAdmin(actor);
        var r =
                repository
                        .findById(id)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        return target(actor, r);
    }

    private Detail target(Long actor, Report r) {
        if (r.getTargetType() == ReportTargetType.COMMENT) {
            var c = comments.target(actor, r.getTargetId());
            return new Detail(Item.of(r), c.authorId(), c.content());
        }
        if (r.getTargetType() == ReportTargetType.POST) {
            var p = posts.target(actor, r.getTargetId());
            return new Detail(Item.of(r), p.authorId(), p.content());
        }
        if (r.getTargetType() == ReportTargetType.MESSAGE) {
            var message = chat.read(actor, r.getTargetId(), true);
            return new Detail(Item.of(r), message.authorId(), message.content());
        }
        if (r.getTargetType() == ReportTargetType.MEMBER)
            return new Detail(
                    Item.of(r),
                    r.getReportedMemberId(),
                    memberLookup.displayName(r.getReportedMemberId()));
        throw new BusinessException(ErrorCode.INVALID_INPUT, "지원하지 않는 신고 대상입니다");
    }

    @Transactional
    public Item review(Long actor, Long id, Review body) {
        access.requireAdmin(actor);
        Report r = em.find(Report.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (r == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        if (r.getStatus() != ReportStatus.RECEIVED)
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이미 처리한 신고입니다");
        String reason = body.reason() == null ? "" : body.reason().strip();
        if (reason.isEmpty() || reason.length() > 200)
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        String action = body.action() == null ? "" : body.action();
        if (!"DISMISS".equals(action)) {
            var target = target(actor, r);
            switch (action) {
                case "HIDE_COMMENT" -> {
                    if (r.getTargetType() != ReportTargetType.COMMENT)
                        throw new BusinessException(ErrorCode.INVALID_INPUT);
                    comments.hide(actor, r.getTargetId());
                }
                case "HIDE_POST" -> {
                    if (r.getTargetType() != ReportTargetType.POST)
                        throw new BusinessException(ErrorCode.INVALID_INPUT);
                    posts.hide(actor, r.getTargetId());
                }
                case "WARN", "SUSPEND", "REQUIRE_NICKNAME" ->
                        members.act(
                                actor,
                                target.memberId(),
                                new AdminMemberManagementService.Action(
                                        action, reason, body.until()));
                default -> throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
        }
        r.review(action, reason, actor, LocalDateTime.now(ZoneId.of("Asia/Seoul")));
        return Item.of(r);
    }
}
