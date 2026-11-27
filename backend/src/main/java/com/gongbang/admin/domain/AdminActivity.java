package com.gongbang.admin.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "admin_activity")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminActivity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_id", nullable = false)
    private Long actorId;

    @Column(nullable = false, length = 40)
    private String action;

    @Column(name = "target_type", nullable = false, length = 30)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(length = 200)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public AdminActivity(Long actor, String action, String type, Long target, String reason) {
        this.actorId = actor;
        this.action = action;
        this.targetType = type;
        this.targetId = target;
        this.reason = reason;
        this.createdAt = LocalDateTime.now(java.time.ZoneId.of("Asia/Seoul"));
    }
}
