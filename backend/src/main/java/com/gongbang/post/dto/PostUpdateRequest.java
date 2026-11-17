package com.gongbang.post.dto;

import java.time.LocalDate;

/**
 * PST-003 모집글 수정 요청 (API 명세서 PATCH /api/posts/{postId}). 보내지 않은(null) 필드는 그대로 둔다.
 * relatedUrl을 빈 문자열로 보내면 지운다.
 */
public record PostUpdateRequest(
        String title,
        Integer targetCount,
        LocalDate recruitStartDate,
        LocalDate recruitEndDate,
        String relatedUrl,
        String content) {}
