package com.gongbang.contest.dto;

import com.gongbang.contest.domain.Category;

/** MAIN-05 분야 목록. 관심 분야·외부 공모전 분야도 이 목록을 쓴다. */
public record CategoryResponse(String categoryCode, String name, int sortOrder) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(
                category.getCode(), category.getName(), category.getSortOrder());
    }
}
