package com.gongbang.contest.domain;

import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import java.util.Arrays;

/** 통합 검색(SRCH-02 ②) 정렬 기준. API 값은 accuracy|latest|deadline. */
public enum ContestSearchSort {
    ACCURACY("accuracy"),
    LATEST("latest"),
    DEADLINE("deadline");

    private final String value;

    ContestSearchSort(String value) {
        this.value = value;
    }

    public static ContestSearchSort from(String value) {
        return Arrays.stream(values())
                .filter(sort -> sort.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_SORT));
    }
}
