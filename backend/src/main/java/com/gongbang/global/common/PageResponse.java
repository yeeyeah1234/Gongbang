package com.gongbang.global.common;

import java.util.List;

/** 목록 응답 (용어 사전 「목록 응답」). page는 0부터 센다. */
public record PageResponse<T>(List<T> items, int page, int size, long totalCount, boolean hasNext) {

    public static <T> PageResponse<T> of(List<T> items, int page, int size, long totalCount) {
        boolean hasNext = (long) (page + 1) * size < totalCount;
        return new PageResponse<>(items, page, size, totalCount, hasNext);
    }
}
