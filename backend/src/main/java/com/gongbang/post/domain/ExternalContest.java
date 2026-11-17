package com.gongbang.post.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 모집글에 직접 입력한 외부 공모전 (용어 사전 「외부 공모전」, recruit_post.ext_*). */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExternalContest {

    @Column(name = "ext_contest_title", length = 50)
    private String title;

    @Column(name = "ext_category_id")
    private Long categoryId;

    /** 접수 마감일. 워크스페이스 종료와 캘린더 자동 일정의 기준이다. */
    @Column(name = "ext_deadline")
    private LocalDate deadline;

    @Column(name = "ext_contest_url", length = 500)
    private String url;

    /** 링크 페이지의 대표 이미지(og:image). 못 불러오면 null이고 화면은 기본 이미지를 쓴다. */
    @Column(name = "ext_contest_image_url", length = 500)
    private String imageUrl;

    private ExternalContest(String title, Long categoryId, LocalDate deadline, String url) {
        this.title = title;
        this.categoryId = categoryId;
        this.deadline = deadline;
        this.url = url;
    }

    public static ExternalContest of(
            String title, Long categoryId, LocalDate deadline, String url) {
        return new ExternalContest(title, categoryId, deadline, url);
    }
}
