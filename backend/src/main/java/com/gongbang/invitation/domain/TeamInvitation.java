package com.gongbang.invitation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * NET-02 · INT-005 팀 초대 엔티티 (`team_invitation`).
 *
 * <p>한 모집글에 같은 회원당 한 행만 유지(`UQ (post_id, invitee_id)`)하며, 거절·취소·탈퇴 후 재초대 시 기존 행을 `PENDING`으로 되돌린다.
 */
@Entity
@Table(
        name = "team_invitation",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_team_invitation_post_invitee",
                        columnNames = {"post_id", "invitee_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TeamInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false, updatable = false)
    private Long postId;

    @Column(name = "inviter_id", nullable = false)
    private Long inviterId;

    @Column(name = "invitee_id", nullable = false, updatable = false)
    private Long inviteeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private InvitationStatus status = InvitationStatus.PENDING;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    private TeamInvitation(Long postId, Long inviterId, Long inviteeId, LocalDateTime createdAt) {
        this.postId = postId;
        this.inviterId = inviterId;
        this.inviteeId = inviteeId;
        this.status = InvitationStatus.PENDING;
        this.createdAt = createdAt;
        this.respondedAt = null;
    }

    public static TeamInvitation create(
            Long postId, Long inviterId, Long inviteeId, LocalDateTime now) {
        return new TeamInvitation(postId, inviterId, inviteeId, now);
    }

    public boolean isPending() {
        return status == InvitationStatus.PENDING;
    }

    public boolean isInvitee(Long memberId) {
        return memberId != null && memberId.equals(inviteeId);
    }

    /** 거절·취소·퇴장 후 재초대 시 기존 행을 다시 대기(`PENDING`) 상태로 되돌린다. */
    public void reinvite(Long inviterId, LocalDateTime now) {
        this.inviterId = inviterId;
        this.status = InvitationStatus.PENDING;
        this.createdAt = now;
        this.respondedAt = null;
    }

    public void accept(LocalDateTime now) {
        this.status = InvitationStatus.ACCEPTED;
        this.respondedAt = now;
    }

    public void decline(LocalDateTime now) {
        this.status = InvitationStatus.DECLINED;
        this.respondedAt = now;
    }

    public void cancel(LocalDateTime now) {
        this.status = InvitationStatus.CANCELED;
        this.respondedAt = now;
    }
}
