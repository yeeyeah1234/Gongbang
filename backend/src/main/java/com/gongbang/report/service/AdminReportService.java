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
    private final com.gongbang.admin.service.AdminActivityWriter audit;
    private final AdminMemberService access;
    private final AdminMemberManagementService members;
    private final MemberService memberLookup;
    private final AdminPostService posts;
    private final CommentModerationService comments;
    private final ReportRepository repository;
    private final EntityManager em;
    private final com.gongbang.chat.service.ChatReportService chat;
    private final com.gongbang.board.service.BoardPostModerationService boardPosts;

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
            LocalDateTime createdAt,
            String title) {
        static Item of(Report r) {
            return of(r, null);
        }

        static Item of(Report r, String title) {
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
                    r.getCreatedAt(),
                    title);
        }
    }

    public record Review(String action, String reason, LocalDateTime until) {}

    public record Detail(
            Item report,
            Long memberId,
            String content,
            LocalDateTime deletedAt,
            String reporterNickname,
            String authorNickname,
            String reviewerNickname,
            boolean reporterAdmin,
            boolean authorAdmin,
            boolean reviewerAdmin) {
        public Detail(Item report, Long memberId, String content, LocalDateTime deletedAt) {
            this(report, memberId, content, deletedAt, null, null, null, false, false, false);
        }

        public Detail(Item report, Long memberId, String content) {
            this(report, memberId, content, null, null, null, null, false, false, false);
        }
    }

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
        var boardTitles =
                boardPosts.reportTitles(
                        actor,
                        rows.stream()
                                .filter(row -> row.getTargetType() == ReportTargetType.BOARD_POST)
                                .map(Report::getTargetId)
                                .distinct()
                                .toList());
        var postTitles =
                posts.reportTitles(
                        actor,
                        rows.stream()
                                .filter(row -> row.getTargetType() == ReportTargetType.POST)
                                .map(Report::getTargetId)
                                .distinct()
                                .toList());
        return new PageImpl<>(
                rows.stream()
                        .map(
                                row ->
                                        Item.of(
                                                row,
                                                switch (row.getTargetType()) {
                                                    case BOARD_POST ->
                                                            boardTitles.getOrDefault(
                                                                    row.getTargetId(),
                                                                    "원문을 찾을 수 없는 글");
                                                    case POST ->
                                                            postTitles.getOrDefault(
                                                                    row.getTargetId(),
                                                                    "원문을 찾을 수 없는 글");
                                                    default -> null;
                                                }))
                        .toList(),
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
        Detail target = target(actor, r);
        return new Detail(
                target.report(),
                target.memberId(),
                target.content(),
                target.deletedAt(),
                memberLookup.displayName(r.getReporterId()),
                memberLookup.displayName(target.memberId()),
                r.getReviewerId() == null ? null : memberLookup.displayName(r.getReviewerId()),
                access.isAdmin(r.getReporterId()),
                access.isAdmin(target.memberId()),
                access.isAdmin(r.getReviewerId()));
    }

    private Detail target(Long actor, Report r) {
        if (r.getTargetType() == ReportTargetType.COMMENT) {
            var c = comments.target(actor, r.getTargetId());
            return new Detail(Item.of(r), c.authorId(), c.content(), c.deletedAt());
        }
        if (r.getTargetType() == ReportTargetType.POST) {
            var p = posts.target(actor, r.getTargetId());
            return new Detail(
                    Item.of(r, p.title()),
                    p.authorId(),
                    p.title() + "\n\n" + p.content(),
                    p.deletedAt());
        }
        if (r.getTargetType() == ReportTargetType.BOARD_POST) {
            var p = boardPosts.target(actor, r.getTargetId());
            return new Detail(
                    Item.of(r, p.title()),
                    p.authorId(),
                    p.title() + "\n\n" + p.content(),
                    p.deletedAt());
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

    private Report lockReport(Long actor, Long id) {
        Report report = em.find(Report.class, id);
        if (report == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        // 같은 글의 다른 신고도 대상 → 신고 순서로 잠근다. 대기 후 신고 상태를 다시 읽는다.
        switch (report.getTargetType()) {
            case POST -> posts.lockForModeration(actor, report.getTargetId());
            case BOARD_POST -> boardPosts.lockForModeration(actor, report.getTargetId());
            case COMMENT -> comments.lockForModeration(actor, report.getTargetId());
            default -> {}
        }
        em.refresh(report, LockModeType.PESSIMISTIC_WRITE);
        return report;
    }

    @Transactional
    public Item review(Long actor, Long id, Review body) {
        access.requireAdmin(actor);
        Report r = lockReport(actor, id);
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
                    r.rememberHiddenAt(comments.hideForReview(actor, r.getTargetId()));
                }
                case "HIDE_POST" -> {
                    if (r.getTargetType() != ReportTargetType.POST)
                        throw new BusinessException(ErrorCode.INVALID_INPUT);
                    r.rememberHiddenAt(posts.hideForReview(actor, r.getTargetId()));
                }
                case "HIDE_BOARD_POST" -> {
                    if (r.getTargetType() != ReportTargetType.BOARD_POST)
                        throw new BusinessException(ErrorCode.INVALID_INPUT);
                    r.rememberHiddenAt(boardPosts.hideForReview(actor, r.getTargetId()));
                }
                case "WARN", "SUSPEND", "SUSPEND_PERMANENT", "REQUIRE_NICKNAME" ->
                        members.act(
                                actor,
                                target.memberId(),
                                new AdminMemberManagementService.Action(
                                        action, reason, body.until()));
                default -> throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
        }
        r.review(action, reason, actor, LocalDateTime.now(ZoneId.of("Asia/Seoul")));
        audit.record(actor, "REPORT_" + action, "REPORT", id, reason);
        return Item.of(r);
    }

    @Transactional
    public Item cancelReview(Long actor, Long id, Review body) {
        access.requireAdmin(actor);
        Report r = lockReport(actor, id);
        if (r.getStatus() == ReportStatus.RECEIVED)
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이미 접수 상태입니다");
        String reason = body.reason() == null ? "" : body.reason().strip();
        if (reason.isEmpty() || reason.length() > 200)
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        if (!Set.of("HIDE_POST", "HIDE_BOARD_POST", "HIDE_COMMENT", "DISMISS")
                .contains(r.getAction()))
            throw new BusinessException(ErrorCode.INVALID_INPUT, "계정 제재는 회원 관리에서 별도로 해제해 주세요");
        if (r.getAppliedHiddenAt() != null) {
            var q = QReport.report;
            var others =
                    new JPAQueryFactory(em)
                            .select(q.id)
                            .from(q)
                            .where(
                                    q.id.ne(id),
                                    q.targetType.eq(r.getTargetType()),
                                    q.targetId.eq(r.getTargetId()),
                                    q.status.eq(ReportStatus.RESOLVED),
                                    q.action.startsWith("HIDE_"))
                            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                            .fetch();
            if (!others.isEmpty())
                throw new BusinessException(ErrorCode.INVALID_INPUT, "같은 글의 다른 숨김 신고를 먼저 취소해 주세요");
            switch (r.getAction()) {
                case "HIDE_POST" ->
                        posts.restoreHidden(actor, r.getTargetId(), r.getAppliedHiddenAt());
                case "HIDE_BOARD_POST" ->
                        boardPosts.restoreHidden(actor, r.getTargetId(), r.getAppliedHiddenAt());
                case "HIDE_COMMENT" ->
                        comments.restoreHidden(actor, r.getTargetId(), r.getAppliedHiddenAt());
                default -> {}
            }
        } else if (r.getAction().startsWith("HIDE_")
                && !r.isHideProvenanceKnown()
                && target(actor, r).deletedAt() != null) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT, "이 신고가 숨긴 글인지 확인할 수 없어 자동 복원할 수 없어요");
        }
        em.createNativeQuery(
                        "INSERT INTO report_review_cancellation (report_id, previous_action, previous_note, previous_reviewer_id, previous_reviewed_at, canceled_by, cancel_reason) VALUES (:id,:action,:note,:reviewer,:time,:actor,:reason)")
                .setParameter("id", id)
                .setParameter("action", r.getAction())
                .setParameter("note", r.getReviewNote())
                .setParameter("reviewer", r.getReviewerId())
                .setParameter("time", r.getReviewedAt())
                .setParameter("actor", actor)
                .setParameter("reason", reason)
                .executeUpdate();
        r.reopen();
        audit.record(actor, "REPORT_CANCEL", "REPORT", id, reason);
        return Item.of(r);
    }
}
