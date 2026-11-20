package com.gongbang.workspace.notice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공지 읽음 기록 (NTC-02). 처음 읽은 시각만 남긴다.
 *
 * <p>행은 {@code NoticeReadRepository#markRead}(네이티브 upsert)로만 넣는다. 엔티티는 스키마 확인(ddl-auto=validate)과
 * 조회용이다.
 */
@Entity
@Table(name = "notice_read")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NoticeRead {

    @EmbeddedId private NoticeReadId id;

    // DB 기본값 CURRENT_TIMESTAMP로 채운다.
    @Column(name = "read_at", insertable = false, updatable = false)
    private LocalDateTime readAt;
}
