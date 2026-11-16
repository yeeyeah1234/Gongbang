package com.gongbang.team.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 모집글 하나에 팀 하나 (post_id UQ). 팀은 삭제하지 않는다 (AGENTS.md 6). */
@Entity
@Table(name = "team")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false, unique = true)
    private Long postId;

    @Column(name = "name", nullable = false, length = 20)
    private String name;

    // DB 기본값 CURRENT_TIMESTAMP로 채운다.
    @Column(name = "last_activity_at", insertable = false)
    private LocalDateTime lastActivityAt;

    // DB가 채우는 공통 컬럼.
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    private Team(Long postId, String name) {
        this.postId = postId;
        this.name = name;
    }

    public static Team create(Long postId, String name) {
        return new Team(postId, name);
    }
}
