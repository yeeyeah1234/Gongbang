package com.gongbang.post.domain;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** PST-007 외부 공모전 모집글의 공모전 사진. sortOrder 0이 대표 이미지이고, 삭제는 소프트 삭제다 (④). */
@Entity
@Table(name = "recruit_post_image")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecruitPostImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false, updatable = false)
    private Long postId;

    @Column(name = "image_url", nullable = false, updatable = false, length = 500)
    private String imageUrl;

    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** 소프트 삭제. 파일은 지우지 않는다 (④). */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // DB가 채우는 공통 컬럼
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    private RecruitPostImage(Long postId, String imageUrl, int sortOrder) {
        this.postId = postId;
        this.imageUrl = imageUrl;
        this.sortOrder = sortOrder;
    }

    /** 새 사진은 맨 뒤에 붙는다. 부르는 쪽이 모집글 행을 잠근 뒤 개수를 세고 부른다. */
    public static RecruitPostImage of(Long postId, String imageUrl, int sortOrder) {
        return new RecruitPostImage(postId, imageUrl, sortOrder);
    }

    public void changeSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public void delete(LocalDateTime now) {
        this.deletedAt = now;
    }
}
