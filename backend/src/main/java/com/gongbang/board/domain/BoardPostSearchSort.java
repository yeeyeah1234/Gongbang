package com.gongbang.board.domain;

import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import java.util.Arrays;

/**
 * 통합 검색(SRCH-02 ②)의 커뮤니티 게시글 정렬. API 값은 accuracy(기본)|latest|deadline. 게시글은 마감일이 없어 deadline은 최신순과
 * 같다.
 */
public enum BoardPostSearchSort {
    ACCURACY("accuracy"),
    LATEST("latest"),
    DEADLINE("deadline");

    private final String value;

    BoardPostSearchSort(String value) {
        this.value = value;
    }

    /** 비어 있으면 정확도순, 모르는 값이면 400 INVALID_INPUT. */
    public static BoardPostSearchSort from(String value) {
        if (value == null || value.isBlank()) {
            return ACCURACY;
        }
        return Arrays.stream(values())
                .filter(sort -> sort.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT));
    }
}
