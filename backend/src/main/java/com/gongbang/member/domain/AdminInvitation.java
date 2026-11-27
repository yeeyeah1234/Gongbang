package com.gongbang.member.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "admin_invitation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminInvitation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "inviter_id", nullable = false)
    private Long inviterId;

    @Column(name = "invitee_id", nullable = false)
    private Long inviteeId;

    @Column(nullable = false, length = 10)
    private String status = "PENDING";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    public AdminInvitation(Long inviterId, Long inviteeId, LocalDateTime now) {
        this.inviterId = inviterId;
        this.inviteeId = inviteeId;
        this.createdAt = now;
    }

    public void respond(boolean accept, LocalDateTime now) {
        status = accept ? "ACCEPTED" : "DECLINED";
        respondedAt = now;
    }
}
