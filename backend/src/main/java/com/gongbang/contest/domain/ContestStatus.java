package com.gongbang.contest.domain;

import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import java.util.Arrays;

/** MAIN-01 ①: active는 진행 중(오늘 <= 마감일)만, all은 마감된 공모전까지. 저장하지 않고 조회 조건으로만 쓴다. */
public enum ContestStatus {
    ACTIVE("active"),
    ALL("all");

    private final String value;

    ContestStatus(String value) {
        this.value = value;
    }

    public static ContestStatus from(String value) {
        return Arrays.stream(values())
                .filter(status -> status.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT));
    }
}
