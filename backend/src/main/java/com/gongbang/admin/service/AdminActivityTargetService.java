package com.gongbang.admin.service;

import com.gongbang.admin.domain.AdminActivity;
import com.gongbang.auth.domain.LoginMember;
import com.gongbang.board.service.BoardPostModerationService;
import com.gongbang.comment.service.CommentModerationService;
import com.gongbang.contest.service.AdminContestService;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.domain.MemberRole;
import com.gongbang.member.service.*;
import com.gongbang.notification.service.ServiceNoticeService;
import com.gongbang.post.service.AdminPostService;
import com.gongbang.report.service.AdminReportService;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 관리자 로그에서 현재 대상 정보를 조회한다. 과거 스냅샷으로 표시하지 않는다. */
@Service
@RequiredArgsConstructor
public class AdminActivityTargetService {
    private final AdminMemberService access;
    private final MemberService members;
    private final AdminPostService posts;
    private final BoardPostModerationService boards;
    private final CommentModerationService comments;
    private final AdminReportService reports;
    private final AdminContestService contests;
    private final ServiceNoticeService notices;
    private final AdminInvitationService invitations;
    private final VerificationReviewService verifications;

    public record Detail(String title, String content, String url) {}

    public Map<String, String> labels(Long actor, List<AdminActivity> rows) {
        access.requireAdmin(actor);
        Map<String, String> result = new HashMap<>();
        Map<String, List<Long>> grouped = new HashMap<>();
        for (var r : rows)
            grouped.computeIfAbsent(r.getTargetType(), key -> new ArrayList<>())
                    .add(r.getTargetId());
        grouped.forEach(
                (type, ids) -> {
                    Map<Long, String> labels =
                            switch (type) {
                                case "POST" -> posts.reportTitles(actor, ids);
                                case "BOARD_POST" -> boards.reportTitles(actor, ids);
                                case "CONTEST" -> contests.titles(actor, ids);
                                case "SERVICE_NOTICE" ->
                                        notices.titles(
                                                new LoginMember(actor, MemberRole.ADMIN), ids);
                                default -> Map.of();
                            };
                    labels.forEach((id, title) -> result.put(type + ":" + id, title));
                    if (type.equals("MEMBER"))
                        members.getProfileSummaries(ids)
                                .forEach((id, m) -> result.put(type + ":" + id, m.nickname()));
                });
        return result;
    }

    public Detail detail(Long actor, String type, Long id) {
        access.requireAdmin(actor);
        return switch (type) {
            case "MEMBER" ->
                    new Detail(
                            members.displayName(id),
                            "현재 회원 닉네임",
                            members.findActiveMember(id).isPresent() ? "/members/" + id : null);
            case "POST" -> {
                var p = posts.target(actor, id);
                yield new Detail(
                        p.title(), p.content(), p.deletedAt() == null ? "/posts/" + id : null);
            }
            case "BOARD_POST" -> {
                var p = boards.target(actor, id);
                yield new Detail(
                        p.title(), p.content(), p.deletedAt() == null ? "/board/" + id : null);
            }
            case "COMMENT" -> {
                var c = comments.target(actor, id);
                yield new Detail(members.displayName(c.authorId()) + "님의 댓글", c.content(), null);
            }
            case "REPORT" -> {
                var r = reports.detail(actor, id);
                yield new Detail(
                        r.report().title() == null ? "신고 #" + id : r.report().title(),
                        r.content(),
                        null);
            }
            case "CONTEST" -> {
                var c = contests.detail(actor, id);
                yield new Detail(
                        c.title(),
                        "마감일: " + c.endDate() + " / 카테고리: " + c.categoryName(),
                        "/contests/" + id);
            }
            case "CATEGORY" -> {
                var c =
                        contests.categories(actor).stream()
                                .filter(x -> x.id().equals(id))
                                .findFirst()
                                .orElseThrow(
                                        () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
                yield new Detail(c.name(), "분류 코드: " + c.code(), null);
            }
            case "SERVICE_NOTICE" -> {
                var n = notices.adminDetail(new LoginMember(actor, MemberRole.ADMIN), id);
                yield new Detail(
                        n.title(),
                        n.content(),
                        n.deletedAt() == null ? "/service-notices/" + id : null);
            }
            case "ADMIN_INVITATION" -> {
                var i = invitations.summary(actor, id);
                yield new Detail(
                        members.displayName(i.inviteeId()) + "님 관리자 초대",
                        "상태: " + i.status() + " / 초대한 관리자: " + members.displayName(i.inviterId()),
                        null);
            }
            case "VERIFICATION" -> {
                var v = verifications.detail(actor, id).item();
                yield new Detail(v.title(), v.nickname() + " / " + v.status(), null);
            }
            default -> throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        };
    }
}
