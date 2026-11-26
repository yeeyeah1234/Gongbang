package com.gongbang.invitation.service;

import com.gongbang.contest.dto.ContestSummary;
import com.gongbang.contest.service.ContestLookupService;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.invitation.domain.InvitationStatus;
import com.gongbang.invitation.domain.TeamInvitation;
import com.gongbang.invitation.dto.InvitationAcceptResponse;
import com.gongbang.invitation.dto.InvitationCreateRequest;
import com.gongbang.invitation.dto.InvitationCreateResponse;
import com.gongbang.invitation.repository.TeamInvitationRepository;
import com.gongbang.invitation.repository.TeamInvitationRepository.InvitationMeta;
import com.gongbang.member.service.MemberService;
import com.gongbang.notification.domain.NotificationType;
import com.gongbang.notification.service.NotificationService;
import com.gongbang.post.domain.PostStatus;
import com.gongbang.post.domain.RecruitPost;
import com.gongbang.post.dto.TeamJoinResult;
import com.gongbang.post.service.PostCommandService;
import com.gongbang.post.service.PostMembershipService;
import com.gongbang.post.service.PostQueryService;
import com.gongbang.post.service.TeamJoinService;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** NET-02 · INT-005 팀 초대 발송·수락·거절 서비스. */
@Service
@RequiredArgsConstructor
public class InvitationCommandService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final TeamInvitationRepository teamInvitationRepository;
    private final PostCommandService postCommandService;
    private final PostQueryService postQueryService;
    private final PostMembershipService postMembershipService;
    private final TeamJoinService teamJoinService;
    private final InvitationCancelService invitationCancelService;
    private final MemberService memberService;
    private final ContestLookupService contestLookupService;
    private final NotificationService notificationService;

    /**
     * 모집글 작성자(팀장)가 다른 활성 회원에게 팀 초대를 보낸다 (`POST /api/invitations`).
     *
     * <p>거절·취소·퇴장 후 재초대하는 경우 기존 행(`UQ (post_id, invitee_id)`)을 `PENDING`으로 되돌린다.
     */
    @Transactional
    public InvitationCreateResponse invite(Long inviterId, InvitationCreateRequest request) {
        if (request == null || request.postId() == null || request.inviteeId() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "초대할 모집글과 회원을 확인해 주세요");
        }
        if (inviterId.equals(request.inviteeId())) {
            throw new BusinessException(ErrorCode.CANNOT_INVITE_SELF);
        }

        RecruitPost post = postQueryService.getActivePost(request.postId());
        if (!post.getAuthorId().equals(inviterId)) {
            throw new BusinessException(ErrorCode.NOT_POST_AUTHOR);
        }
        if (!post.isRecruiting()) {
            throw new BusinessException(ErrorCode.POST_COMPLETED);
        }
        if (post.isFull()) {
            throw new BusinessException(ErrorCode.POST_FULL);
        }

        boolean activeInvitee =
                memberService
                        .findActiveMember(request.inviteeId())
                        .filter(member -> !member.isSuspended())
                        .isPresent();
        if (!activeInvitee) {
            throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND, "초대할 회원을 찾을 수 없어요");
        }
        if (postMembershipService.isActiveMember(post.getId(), request.inviteeId())) {
            throw new BusinessException(ErrorCode.ALREADY_TEAM_MEMBER);
        }

        LocalDateTime now = LocalDateTime.now(KST);
        TeamInvitation invitation =
                saveOrReinvite(post.getId(), inviterId, request.inviteeId(), now);

        sendInviteNotification(post, inviterId, request.inviteeId(), invitation.getId());
        return new InvitationCreateResponse(invitation.getId());
    }

    /**
     * 초대받은 본인이 팀 초대를 수락한다 (`POST /api/invitations/{invitationId}/accept`).
     *
     * <p>잠금 순서(`recruit_post → team_invitation → application → team`)와 `READ COMMITTED` 격리 수준을 지켜
     * 지원 수락·모집글 삭제와 동시 실행돼도 데드락이나 정원 초과가 나지 않는다. `joinByInvitation` 호출 전에 초대를 먼저 `ACCEPTED`로
     * 반영(`flush`)해 두어, 정원 마감으로 대기 초대가 일괄 취소될 때 본인 초대가 함께 취소되지 않게 한다.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public InvitationAcceptResponse accept(Long invitationId, Long memberId) {
        InvitationMeta meta =
                teamInvitationRepository
                        .findMetaById(invitationId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.INVITATION_NOT_FOUND));
        if (!memberId.equals(meta.getInviteeId())) {
            throw new BusinessException(ErrorCode.NOT_INVITEE);
        }
        if (meta.getStatus() != InvitationStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVITATION_CLOSED);
        }

        LocalDateTime now = LocalDateTime.now(KST);
        postCommandService.lockPost(meta.getPostId());

        TeamInvitation invitation =
                teamInvitationRepository
                        .findByIdForUpdate(invitationId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.INVITATION_NOT_FOUND));
        if (!invitation.isInvitee(memberId)) {
            throw new BusinessException(ErrorCode.NOT_INVITEE);
        }
        if (!invitation.isPending()) {
            throw new BusinessException(ErrorCode.INVITATION_CLOSED);
        }
        invitation.accept(now);
        teamInvitationRepository.flush();

        TeamJoinResult joinResult =
                teamJoinService.joinByInvitation(meta.getPostId(), memberId, now);

        if (joinResult.postStatus() == PostStatus.COMPLETED) {
            invitationCancelService.cancelPendingByPostId(meta.getPostId(), now);
        }
        return new InvitationAcceptResponse(joinResult.teamId());
    }

    /** 초대받은 본인이 팀 초대를 거절한다 (`POST /api/invitations/{invitationId}/decline`). */
    @Transactional
    public void decline(Long invitationId, Long memberId) {
        TeamInvitation invitation =
                teamInvitationRepository
                        .findByIdForUpdate(invitationId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.INVITATION_NOT_FOUND));
        if (!invitation.isInvitee(memberId)) {
            throw new BusinessException(ErrorCode.NOT_INVITEE);
        }
        if (!invitation.isPending()) {
            throw new BusinessException(ErrorCode.INVITATION_CLOSED);
        }
        invitation.decline(LocalDateTime.now(KST));
    }

    private TeamInvitation saveOrReinvite(
            Long postId, Long inviterId, Long inviteeId, LocalDateTime now) {
        Optional<TeamInvitation> existing =
                teamInvitationRepository.findByPostIdAndInviteeIdForUpdate(postId, inviteeId);
        if (existing.isPresent()) {
            TeamInvitation invitation = existing.get();
            if (invitation.isPending()) {
                throw new BusinessException(ErrorCode.ALREADY_INVITED);
            }
            invitation.reinvite(inviterId, now);
            return invitation;
        }
        try {
            return teamInvitationRepository.saveAndFlush(
                    TeamInvitation.create(postId, inviterId, inviteeId, now));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.ALREADY_INVITED);
        }
    }

    private void sendInviteNotification(
            RecruitPost post, Long inviterId, Long inviteeId, Long invitationId) {
        String inviterNickname = memberService.displayName(inviterId);
        String contestTitle = resolveContestTitle(post);
        String targetLabel =
                contestTitle != null && !contestTitle.isBlank()
                        ? contestTitle + " · " + post.getTitle()
                        : post.getTitle();
        String message = "‘" + inviterNickname + "’님이 ‘" + targetLabel + "’ 팀에 초대했어요";
        notificationService.send(
                inviteeId,
                NotificationType.TEAM_INVITE,
                inviterId,
                "INVITATION",
                invitationId,
                message,
                "/posts/" + post.getId());
    }

    private String resolveContestTitle(RecruitPost post) {
        if (post.getContestId() != null) {
            return contestLookupService
                    .findSummary(post.getContestId())
                    .map(ContestSummary::title)
                    .orElse(null);
        }
        if (post.getExternalContest() != null) {
            return post.getExternalContest().getTitle();
        }
        return null;
    }
}
