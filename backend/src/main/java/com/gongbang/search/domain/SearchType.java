package com.gongbang.search.domain;

import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import java.util.Arrays;

/** SRCH-02 ① 결과 유형 탭: [전체] [공모전] [커뮤니티]. API 값은 all|contest|post. */
public enum SearchType {
    ALL("all"),
    CONTEST("contest"),
    POST("post");

    private final String value;

    SearchType(String value) {
        this.value = value;
    }

    public static SearchType from(String value) {
        return Arrays.stream(values())
                .filter(type -> type.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT));
    }

    public boolean includesContests() {
        return this == ALL || this == CONTEST;
    }
}
