package com.gongbang.member.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** MY-03 북마크 (member_id, contest_id 복합 키) */
@Entity
@Table(name = "bookmark")
@IdClass(Bookmark.Key.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Bookmark {

    @Id
    @Column(name = "member_id")
    private Long memberId;

    @Id
    @Column(name = "contest_id")
    private Long contestId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public Bookmark(Long memberId, Long contestId) {
        this.memberId = memberId;
        this.contestId = contestId;
    }

    @Getter
    @NoArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        private Long memberId;
        private Long contestId;

        public Key(Long memberId, Long contestId) {
            this.memberId = memberId;
            this.contestId = contestId;
        }
    }
}
