package com.gongbang.contest.domain;

import com.gongbang.contest.dto.ContestBatchItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "contest")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Contest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_url", nullable = false, unique = true, length = 500)
    private String sourceUrl;

    @Column(name = "source_site", nullable = false, length = 30)
    private String sourceSite;

    // MAIN-02 ④ [바로가기]: 주최 측 원래 공모전 페이지 (위비티 「홈페이지」 칸). 없으면 null
    @Column(name = "homepage_url", length = 500)
    private String homepageUrl;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "host", length = 100)
    private String host;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "view_count", nullable = false)
    private int viewCount = 0;

    // DB가 채우는 공통 컬럼. 최신순(MAIN-04) 정렬에 쓴다.
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    private Contest(String sourceUrl, Category category, ContestBatchItem item) {
        this.sourceUrl = sourceUrl;
        applyItem(category, item);
    }

    public static Contest create(String sourceUrl, Category category, ContestBatchItem item) {
        return new Contest(sourceUrl, category, item);
    }

    public void update(Category category, ContestBatchItem item) {
        applyItem(category, item);
    }

    private void applyItem(Category category, ContestBatchItem item) {
        this.sourceSite = item.sourceSite();
        this.homepageUrl = item.homepageUrl();
        this.title = item.title();
        this.category = category;
        this.host = item.host();
        this.thumbnailUrl = item.thumbnailUrl();
        this.startDate = item.startDate();
        this.endDate = item.deadline();
        this.description = item.description();
    }
}
