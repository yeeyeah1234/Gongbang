package com.gongbang.contest.dto;

import com.gongbang.contest.domain.ContestSort;
import com.gongbang.contest.domain.ContestStatus;

/**
 * GET /api/contests 조회 조건. categoryCode·keyword가 null이면 그 조건은 걸지 않는다.
 *
 * @param keyword 앞뒤 공백을 지운 값. 모집글 작성의 공모전 검색(INT-001)에도 쓰인다.
 */
public record ContestSearchCondition(
        ContestStatus status, ContestSort sort, String categoryCode, String keyword) {}
