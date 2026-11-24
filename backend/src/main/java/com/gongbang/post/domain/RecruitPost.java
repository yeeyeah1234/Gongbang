package com.gongbang.post.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 팀원 모집글. 서비스 공모전(contestId)과 외부 공모전(externalContest) 중 한쪽만 채운다. */
@Entity
@Table(name = "recruit_post")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecruitPost {

    /** 글을 쓰면 작성자 한 명이 확정 인원이다 (PST-002 ⑦). */
    private static final int INITIAL_COUNT = 1;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // TODO(확인 필요): member 엔티티(정진우)가 생기면 연관관계로 바꿀지 정한다. 지금은 ID만 둔다.
    @Column(name = "author_id", nullable = false, updatable = false)
    private Long authorId;

    /** 서비스 공모전. 외부 공모전이면 null. 등록 후 바꾸지 않는다 (PST-003 ②). */
    @Column(name = "contest_id", updatable = false)
    private Long contestId;

    @Embedded private ExternalContest externalContest;

    @Column(name = "title", nullable = false, length = 50)
    private String title;

    @Column(name = "content", nullable = false, length = 2000)
    private String content;

    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "target_count", nullable = false)
    private int targetCount;

    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "current_count", nullable = false)
    private int currentCount = INITIAL_COUNT;

    @Column(name = "recruit_start_date", nullable = false)
    private LocalDate recruitStartDate;

    @Column(name = "recruit_end_date", nullable = false)
    private LocalDate recruitEndDate;

    @Column(name = "related_url", length = 500)
    private String relatedUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private PostStatus status = PostStatus.RECRUITING;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "view_count", nullable = false)
    private int viewCount = 0;

    /** 소프트 삭제. null이 아니면 목록·검색·댓글에서 숨긴다 (PST-004). */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // DB가 채우는 공통 컬럼
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    private RecruitPost(
            Long authorId,
            Long contestId,
            ExternalContest externalContest,
            String title,
            String content,
            int targetCount,
            LocalDate recruitStartDate,
            LocalDate recruitEndDate,
            String relatedUrl) {
        if ((contestId == null) == (externalContest == null)) {
            throw new IllegalArgumentException("서비스 공모전과 외부 공모전 중 하나만 있어야 합니다");
        }
        this.authorId = authorId;
        this.contestId = contestId;
        this.externalContest = externalContest;
        this.title = title;
        this.content = content;
        this.targetCount = targetCount;
        this.recruitStartDate = recruitStartDate;
        this.recruitEndDate = recruitEndDate;
        this.relatedUrl = relatedUrl;
    }

    /**
     * PST-003 수정. 연결된 공모전과 대표 이미지는 바꾸지 않는다 (②). 모집완료 글은 목표 인원을 늘려도 모집중으로 돌아가지 않는다 (⑤). 값 검증은
     * PostCommandService가 한다.
     */
    public void update(
            String title,
            String content,
            int targetCount,
            LocalDate recruitStartDate,
            LocalDate recruitEndDate,
            String relatedUrl) {
        this.title = title;
        this.content = content;
        this.targetCount = targetCount;
        this.recruitStartDate = recruitStartDate;
        this.recruitEndDate = recruitEndDate;
        this.relatedUrl = relatedUrl;
    }

    /** PST-004 소프트 삭제. 팀과 워크스페이스는 그대로 남고, 공모전 정보는 이 글에서 계속 읽는다 (④). */
    public void delete(LocalDateTime now) {
        this.deletedAt = now;
    }

    public void forceHide(LocalDateTime now) {
        this.deletedAt = now;
        this.status = PostStatus.CLOSED;
    }

    public boolean isRecruiting() {
        return status == PostStatus.RECRUITING;
    }

    /** 확정 인원이 목표 인원에 닿았는지 (REC-004 ③). */
    public boolean isFull() {
        return currentCount >= targetCount;
    }

    /** REC-004 ②, INT-005 ①: 확정 팀원이 한 명 늘었다. 부르는 쪽이 이 행을 잠근 뒤 정원을 확인하고 부른다. */
    public void addConfirmedMember() {
        this.currentCount++;
    }

    /**
     * INT-006 ④⑤: 확정 팀원이 한 명 나갔다(나가기·강제 퇴장·탈퇴). 모집완료 상태는 그대로 둔다. 부르는 쪽이 이 행을 잠근 뒤 부른다. 작성자(팀장)는 나갈
     * 수 없으므로 1 아래로는 내려가지 않는다.
     */
    public void removeConfirmedMember() {
        if (currentCount <= 1) {
            throw new IllegalStateException("확정 인원은 작성자를 포함해 1명 아래로 내려갈 수 없습니다: postId=" + id);
        }
        this.currentCount--;
    }

    /** REC-006 모집완료. 되돌리지 않는다 (REC-007). */
    public void complete(LocalDateTime now) {
        this.status = PostStatus.COMPLETED;
        this.completedAt = now;
    }

    /** PST-002: 서비스 공모전으로 작성. */
    public static RecruitPost withContest(
            Long authorId,
            Long contestId,
            String title,
            String content,
            int targetCount,
            LocalDate recruitStartDate,
            LocalDate recruitEndDate,
            String relatedUrl) {
        return new RecruitPost(
                authorId,
                contestId,
                null,
                title,
                content,
                targetCount,
                recruitStartDate,
                recruitEndDate,
                relatedUrl);
    }

    /** PST-002: 외부 공모전으로 작성. */
    public static RecruitPost withExternalContest(
            Long authorId,
            ExternalContest externalContest,
            String title,
            String content,
            int targetCount,
            LocalDate recruitStartDate,
            LocalDate recruitEndDate,
            String relatedUrl) {
        return new RecruitPost(
                authorId,
                null,
                externalContest,
                title,
                content,
                targetCount,
                recruitStartDate,
                recruitEndDate,
                relatedUrl);
    }
}
