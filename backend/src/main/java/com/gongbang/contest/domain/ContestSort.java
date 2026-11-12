package com.gongbang.contest.domain;

import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import java.util.Arrays;

/** MAIN-04 ② 정렬 기준. API 값은 latest|deadline|views. */
public enum ContestSort {
    LATEST("latest"),
    DEADLINE("deadline"),
    VIEWS("views");

    private final String value;

    ContestSort(String value) {
        this.value = value;
    }

    public static ContestSort from(String value) {
        return Arrays.stream(values())
                .filter(sort -> sort.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_SORT));
    }
}
