package com.gongbang.contest.dto;

import org.springframework.http.ResponseCookie;

/** 상세 응답과, 응답에 함께 내려줄 「오늘 본 공모전」 쿠키 (MAIN-02 ⑤). */
public record ContestDetailView(ContestDetailResponse detail, ResponseCookie viewedCookie) {}
