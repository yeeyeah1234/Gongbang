package com.gongbang.post.dto;

/** REC-006 모집 완료 응답. 이번 완료로 자동 거절된 대기 지원 수. */
public record PostCompleteResponse(int autoRejectedCount) {}
