package com.gongbang.contest.domain;

import com.gongbang.contest.dto.ContestBatchItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
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

    @Column(name = "source_site", nullable = false, length = 50)
    private String sourceSite;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "host")
    private String host;

    @Column(name = "category_code", nullable = false, length = 50)
    private String categoryCode;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "description", columnDefinition = "LONGTEXT")
    private String description;

    private Contest(String sourceUrl, ContestBatchItem item) {
        this.sourceUrl = sourceUrl;
        applyItem(item);
    }

    public static Contest create(String sourceUrl, ContestBatchItem item) {
        return new Contest(sourceUrl, item);
    }

    public void update(ContestBatchItem item) {
        applyItem(item);
    }

    private void applyItem(ContestBatchItem item) {
        this.sourceSite = item.sourceSite();
        this.title = item.title();
        this.host = item.host();
        this.categoryCode = item.categoryCode();
        this.thumbnailUrl = item.thumbnailUrl();
        this.startDate = item.startDate();
        this.endDate = item.deadline();
        this.description = item.description();
    }
}
