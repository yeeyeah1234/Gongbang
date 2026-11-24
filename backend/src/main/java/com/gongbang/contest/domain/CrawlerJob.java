package com.gongbang.contest.domain;

import jakarta.persistence.*;
import java.time.*;
import lombok.*;

@Entity
@Table(name = "crawler_job")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CrawlerJob {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long requestedBy;

    @Column(length = 10)
    private String status = "PENDING";

    @Column(insertable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    public CrawlerJob(Long actor) {
        requestedBy = actor;
    }

    public void start() {
        status = "RUNNING";
        startedAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
    }

    public void finish(boolean success) {
        status = success ? "SUCCEEDED" : "FAILED";
        finishedAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
    }
}
