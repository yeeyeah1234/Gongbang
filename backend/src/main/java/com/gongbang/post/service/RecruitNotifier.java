package com.gongbang.post.service;

import com.gongbang.member.service.MemberService;
import com.gongbang.notification.domain.NotificationType;
import com.gongbang.notification.service.NotificationService;
import com.gongbang.post.domain.RecruitPost;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** INT-003 모집 알림. 발생 조건별 문구와 이동 주소를 한 곳에 둔다 (알림 화면·수신 설정은 NotificationService가 처리). */
@Component
@RequiredArgsConstructor
public class RecruitNotifier {

    static final String TARGET_TYPE = "POST";

    private final NotificationService notificationService;
    private final MemberService memberService;

    /**
     * INT-003 ①: 새 지원 → 작성자. 누르면 지원자 관리 모달. 닉네임은 MemberService.displayName (탈퇴 회원은 「탈퇴한 사용자」, 예외를
     * 던지지 않아 트랜잭션을 깨지 않는다).
     */
    public void newApplication(RecruitPost post, Long applicantId) {
        String message =
                String.format(
                        "‘%s’님이 ‘%s’에 지원했어요",
                        memberService.displayName(applicantId), post.getTitle());
        notificationService.send(
                post.getAuthorId(),
                NotificationType.APPLY_NEW,
                applicantId,
                TARGET_TYPE,
                post.getId(),
                message,
                applicantsLink(post.getId()));
    }

    /** INT-003 ②: 수락 → 지원자. 누르면 모집글 상세. */
    public void accepted(RecruitPost post, Long applicantId, Long actorId) {
        send(applicantId, NotificationType.APPLY_ACCEPTED, actorId, post, "‘%s’ 팀에 합류했어요");
    }

    /** INT-003 ③: 거절(자동 거절 포함) → 지원자. 누르면 모집글 상세. 자동 거절은 actorId null(시스템). */
    public void rejected(RecruitPost post, Long applicantId, Long actorId) {
        send(applicantId, NotificationType.APPLY_REJECTED, actorId, post, "‘%s’ 지원이 거절되었어요");
    }

    /** INT-003 ④: 모집글 삭제 → 대기 중이던 지원자. 누르면 모집글 목록(삭제된 글이라 상세로 보내지 않음). */
    public void postDeleted(RecruitPost post, Long applicantId, Long actorId) {
        notificationService.send(
                applicantId,
                NotificationType.POST_DELETED,
                actorId,
                TARGET_TYPE,
                post.getId(),
                String.format("‘%s’ 모집글이 삭제되었어요", post.getTitle()),
                LIST_LINK);
    }

    private void send(
            Long receiverId, NotificationType type, Long actorId, RecruitPost post, String format) {
        notificationService.send(
                receiverId,
                type,
                actorId,
                TARGET_TYPE,
                post.getId(),
                String.format(format, post.getTitle()),
                detailLink(post.getId()));
    }

    /** 모집글 목록 화면 주소. */
    static final String LIST_LINK = "/posts";

    /** 모집글 상세 화면 주소 (프론트 경로 /posts/{postId}). */
    static String detailLink(Long postId) {
        return "/posts/" + postId;
    }

    /**
     * 지원자 관리 모달을 연 모집글 상세 주소. 모달은 상세 화면 위에 뜨므로 상세 주소에 표시를 붙인다. 프론트 모집글 화면(김수형)에서 이 값을 읽어 모달을 연다.
     */
    static String applicantsLink(Long postId) {
        return detailLink(postId) + "?applicants=open";
    }
}
