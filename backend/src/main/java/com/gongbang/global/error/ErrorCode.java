package com.gongbang.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 400 Bad Request
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "잘못된 입력값입니다."),
    // TODO(확인 필요): 기능정의서에 문구가 없어 임시로 정함 (API 명세서 MAIN-04)
    INVALID_SORT(HttpStatus.BAD_REQUEST, "INVALID_SORT", "정렬 기준이 올바르지 않습니다."),
    // SRCH-03 ① 문구
    KEYWORD_TOO_SHORT(HttpStatus.BAD_REQUEST, "KEYWORD_TOO_SHORT", "검색어는 2글자 이상 입력해주세요"),

    // 401 Unauthorized
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증이 필요하거나 만료되었습니다."),
    INVALID_INTERNAL_KEY(HttpStatus.UNAUTHORIZED, "INVALID_INTERNAL_KEY", "내부 키가 올바르지 않습니다."),

    // 403 Forbidden
    NOT_TEAM_MEMBER(HttpStatus.FORBIDDEN, "NOT_TEAM_MEMBER", "팀원만 접근할 수 있습니다."),
    NOT_TEAM_LEADER(HttpStatus.FORBIDDEN, "NOT_TEAM_LEADER", "팀장만 접근할 수 있습니다."),

    // 404 Not Found
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "POST_NOT_FOUND", "존재하지 않거나 삭제된 자원입니다."),
    // TODO(확인 필요): 기능정의서에 문구가 없어 임시로 정함 (API 명세서 MAIN-02)
    CONTEST_NOT_FOUND(HttpStatus.NOT_FOUND, "CONTEST_NOT_FOUND", "존재하지 않는 공모전입니다."),

    // 409 Conflict
    ALREADY_APPLIED(HttpStatus.CONFLICT, "ALREADY_APPLIED", "이미 지원한 모집글입니다."),
    POST_FULL(HttpStatus.CONFLICT, "POST_FULL", "모집 인원이 모두 충원되었습니다."),
    DOCUMENT_LIMIT(HttpStatus.CONFLICT, "DOCUMENT_LIMIT", "문서 개수 제한을 초과했습니다."),

    // 429 Too Many Requests
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS", "요청 횟수를 초과했습니다."),

    // 500 Internal Server Error
    INTERNAL_SERVER_ERROR(
            HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
