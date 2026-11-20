package com.gongbang.workspace.notice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** notice_read의 PK (notice_id, member_id). */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class NoticeReadId implements Serializable {

    @Column(name = "notice_id", nullable = false)
    private Long noticeId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;
}
