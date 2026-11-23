package com.gongbang.board.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** BRD 커뮤니티 게시글. 팀원 모집글(recruit_post)과 별개다. 값 검증은 BoardPostCommandService가 한다. */
@Entity
@Table(name = "board_post")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BoardPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "author_id", nullable = false, updatable = false)
    private Long authorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 10)
    private BoardCategory category;

    /** 연결된 서비스 공모전. 공모전 이야기는 필수, 나머지는 선택 (BRD-002 ①). */
    @Column(name = "contest_id")
    private Long contestId;

    @Column(name = "title", nullable = false, length = 50)
    private String title;

    @Column(name = "content", nullable = false, length = 2000)
    private String content;

    /** 결과물 링크. 결과물 자랑에서만 둔다 (BRD-002 ②). */
    @Column(name = "link_url", length = 500)
    private String linkUrl;

    /** BRD-004 ⑤. 동시에 열어도 빠지지 않게 저장소에서 바로 1 올린다. */
    @Column(name = "view_count", nullable = false)
    private int viewCount = 0;

    /** BRD-007. 좋아요·취소 때 이 행을 잠그고 바꾼다. */
    @Column(name = "like_count", nullable = false)
    private int likeCount = 0;

    /** 소프트 삭제. null이 아니면 목록·상세에서 숨긴다 (BRD-003 ④). */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // DB가 채우는 공통 컬럼
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    private BoardPost(
            Long authorId,
            BoardCategory category,
            Long contestId,
            String title,
            String content,
            String linkUrl) {
        this.authorId = authorId;
        this.category = category;
        this.contestId = contestId;
        this.title = title;
        this.content = content;
        this.linkUrl = linkUrl;
    }

    public static BoardPost of(
            Long authorId,
            BoardCategory category,
            Long contestId,
            String title,
            String content,
            String linkUrl) {
        return new BoardPost(authorId, category, contestId, title, content, linkUrl);
    }

    /** BRD-003 ② 수정. 말머리를 바꾸면 결과물 링크는 부르는 쪽이 넘긴 값(결과물 자랑이 아니면 null)으로 바뀐다. */
    public void update(
            BoardCategory category, Long contestId, String title, String content, String linkUrl) {
        this.category = category;
        this.contestId = contestId;
        this.title = title;
        this.content = content;
        this.linkUrl = linkUrl;
    }

    public void delete(LocalDateTime now) {
        this.deletedAt = now;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isWrittenBy(Long memberId) {
        return authorId.equals(memberId);
    }

    public void increaseLikeCount() {
        this.likeCount++;
    }

    public void decreaseLikeCount() {
        if (likeCount > 0) {
            this.likeCount--;
        }
    }
}
