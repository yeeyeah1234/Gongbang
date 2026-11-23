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
    // PST-002 ⑥ 문구
    CONTEST_REQUIRED(HttpStatus.BAD_REQUEST, "CONTEST_REQUIRED", "공모전을 선택해 주세요"),
    // PST-002 ②⑥ 문구
    CONTEST_ENDED(HttpStatus.BAD_REQUEST, "CONTEST_ENDED", "이미 마감된 공모전이에요"),
    // PST-002 ⑥ 문구 (서버 기간 오류)
    INVALID_PERIOD(HttpStatus.BAD_REQUEST, "INVALID_PERIOD", "모집 기간을 다시 확인해 주세요"),
    // PST-003 ④ 문구
    TARGET_BELOW_CURRENT(
            HttpStatus.BAD_REQUEST, "TARGET_BELOW_CURRENT", "현재 확정된 팀원 수보다 적게 설정할 수 없습니다"),
    // PST-003 ④ 문구 (모집중인 글, 11/13 결정)
    TARGET_EQUALS_CURRENT(
            HttpStatus.BAD_REQUEST,
            "TARGET_EQUALS_CURRENT",
            "현재 확정된 팀원 수와 같게 설정할 수 없습니다. 모집을 마치려면 「모집 완료」를 눌러 주세요"),
    // TODO(확인 필요): 기능정의서 문구를 찾지 못해 임시로 정함 (AGENTS.md 5번 팀 이름 1~20자, WS-01)
    TEAM_NAME_INVALID(HttpStatus.BAD_REQUEST, "TEAM_NAME_INVALID", "팀 이름은 1~20자로 입력해 주세요"),

    // ---- 정진우 (AUTH·MY·NOTI) 400 ----
    // AUTH-05 ③ 문구. 남은 횟수는 메시지에 넣는다
    CODE_MISMATCH(HttpStatus.BAD_REQUEST, "CODE_MISMATCH", "인증번호가 맞지 않아요"),
    // AUTH-05 ③ 문구
    CODE_EXPIRED(HttpStatus.BAD_REQUEST, "CODE_EXPIRED", "인증 시간이 지났어요. 인증번호를 다시 받아 주세요"),
    // TODO(확인 필요): 기능정의서에 문구가 없어 임시로 정함 (AUTH-06 ③)
    NICKNAME_INVALID(HttpStatus.BAD_REQUEST, "NICKNAME_INVALID", "닉네임은 2~10자 한글·영문·숫자로 입력해 주세요"),
    // TODO(확인 필요): 기능정의서에 문구가 없어 임시로 정함 (AUTH-06 ④)
    NICKNAME_FORBIDDEN(HttpStatus.BAD_REQUEST, "NICKNAME_FORBIDDEN", "쓸 수 없는 단어가 들어 있어요"),
    // TODO(확인 필요): 기능정의서에 문구가 없어 임시로 정함 (AUTH-04 ②)
    TERMS_REQUIRED(HttpStatus.BAD_REQUEST, "TERMS_REQUIRED", "필수 약관에 동의해 주세요"),
    // TODO(확인 필요): 기능정의서에 문구가 없어 임시로 정함 (AUTH-02 ③, AUTH-05 ⑤)
    TOKEN_EXPIRED(HttpStatus.BAD_REQUEST, "TOKEN_EXPIRED", "인증 시간이 지났어요. 처음부터 다시 진행해 주세요"),
    // TODO(확인 필요): 기능정의서에 문구가 없어 임시로 정함 (AUTH-06 ①)
    PASSWORD_POLICY(
            HttpStatus.BAD_REQUEST, "PASSWORD_POLICY", "비밀번호는 8~20자, 영문·숫자·특수문자를 각 1개 이상 넣어 주세요"),
    // AUTH-02 ④, MY-04 ② 문구
    SAME_AS_OLD_PASSWORD(HttpStatus.BAD_REQUEST, "SAME_AS_OLD_PASSWORD", "지금 쓰는 비밀번호와 다르게 정해 주세요"),
    // MY-04 ③ 문구 (틀린 횟수는 메시지에 넣는다)
    PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "PASSWORD_MISMATCH", "현재 비밀번호가 맞지 않아요"),
    // TODO(확인 필요): 기능정의서에 문구가 없어 임시로 정함 (MY-11 ②)
    CONFIRM_TEXT_MISMATCH(HttpStatus.BAD_REQUEST, "CONFIRM_TEXT_MISMATCH", "「탈퇴합니다」를 정확히 입력해 주세요"),
    // TODO(확인 필요): 기능정의서에 문구가 없어 임시로 정함 (MY-01 ③)
    SKILL_TAG_LIMIT(HttpStatus.BAD_REQUEST, "SKILL_TAG_LIMIT", "기술 태그는 10개까지 넣을 수 있어요"),
    // TODO(확인 필요): 기능정의서에 문구가 없어 임시로 정함 (MY-08 ②, MY-09 ②)
    FUTURE_DATE(HttpStatus.BAD_REQUEST, "FUTURE_DATE", "오늘 이후 날짜는 고를 수 없어요"),
    // TODO(확인 필요): 기능정의서에 문구가 없어 임시로 정함 (MY-08 ③)
    PROOF_REQUIRED(HttpStatus.BAD_REQUEST, "PROOF_REQUIRED", "증빙 파일을 올려 주세요"),

    // AUTH-10 ③: 1회용 키가 없거나 1분이 지남 (문구는 ⑧과 같은 뜻으로 임시)
    OAUTH_CODE_INVALID(
            HttpStatus.BAD_REQUEST, "OAUTH_CODE_INVALID", "간편 로그인을 완료하지 못했어요. 다시 시도해 주세요"),
    // NET-04, NET-06 (API 명세서)
    CANNOT_CHAT_SELF(HttpStatus.BAD_REQUEST, "CANNOT_CHAT_SELF", "자기 자신과는 1:1 채팅을 할 수 없어요"),
    CANNOT_KICK_SELF(HttpStatus.BAD_REQUEST, "CANNOT_KICK_SELF", "자기 자신은 강제 퇴장할 수 없어요"),

    // 401 Unauthorized
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증이 필요하거나 만료되었습니다."),
    INVALID_INTERNAL_KEY(HttpStatus.UNAUTHORIZED, "INVALID_INTERNAL_KEY", "내부 키가 올바르지 않습니다."),
    // AUTH-01 ② 문구 (틀린 횟수는 메시지에 넣는다)
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "LOGIN_FAILED", "이메일 또는 비밀번호가 맞지 않아요"),

    // ---- 정진우 403 ----
    // AUTH-01 ③ 문구
    EXTRA_VERIFICATION_REQUIRED(
            HttpStatus.FORBIDDEN,
            "EXTRA_VERIFICATION_REQUIRED",
            "로그인을 5번 실패했어요. 가입한 이메일로 보낸 인증번호를 입력해 주세요"),
    // TODO(확인 필요): 기능정의서에 문구가 없어 임시로 정함 (AUTH-01 ④)
    ACCOUNT_LOCKED(HttpStatus.FORBIDDEN, "ACCOUNT_LOCKED", "로그인 시도가 반복돼 30분 동안 로그인할 수 없어요"),
    // TODO(확인 필요): 기능정의서에 문구가 없어 임시로 정함 (AGENTS 9)
    NOT_ADMIN(HttpStatus.FORBIDDEN, "NOT_ADMIN", "운영자만 할 수 있어요"),
    // TODO(확인 필요): API 명세서에 「403」만 있고 코드가 없어 임시로 정함 (MY-07·08·09, NOTI-01)
    NOT_OWNER(HttpStatus.FORBIDDEN, "NOT_OWNER", "본인 것만 바꿀 수 있어요"),

    // 403 Forbidden
    NOT_TEAM_MEMBER(HttpStatus.FORBIDDEN, "NOT_TEAM_MEMBER", "팀원만 접근할 수 있습니다."),
    NOT_TEAM_LEADER(HttpStatus.FORBIDDEN, "NOT_TEAM_LEADER", "팀장만 접근할 수 있습니다."),
    // PST-006 ⑧ 문구
    NOT_POST_AUTHOR(HttpStatus.FORBIDDEN, "NOT_POST_AUTHOR", "모집글 작성자만 할 수 있어요"),
    // PST-006 ⑧, REC-002 ⑤ 문구
    NOT_APPLICANT(HttpStatus.FORBIDDEN, "NOT_APPLICANT", "본인의 지원만 취소할 수 있어요"),
    // REC-001 ③ 문구
    CANNOT_APPLY_OWN_POST(HttpStatus.FORBIDDEN, "CANNOT_APPLY_OWN_POST", "내가 쓴 모집글에는 지원할 수 없어요"),
    // BRD-003 ① 문구
    NOT_BOARD_POST_AUTHOR(HttpStatus.FORBIDDEN, "NOT_BOARD_POST_AUTHOR", "글 작성자만 할 수 있어요"),
    // BRD-007 ③ 문구
    CANNOT_LIKE_OWN_BOARD_POST(
            HttpStatus.FORBIDDEN, "CANNOT_LIKE_OWN_BOARD_POST", "내 글에는 좋아요를 누를 수 없어요"),
    NOT_EVENT_AUTHOR(HttpStatus.FORBIDDEN, "NOT_EVENT_AUTHOR", "작성자나 팀장만 바꿀 수 있어요"),
    AUTO_EVENT_NOT_EDITABLE(HttpStatus.FORBIDDEN, "AUTO_EVENT_NOT_EDITABLE", "자동 일정은 바꿀 수 없어요"),
    // TODO(확인 필요): 기능정의서 문구를 찾지 못해 임시로 정함 (DOC-04)
    NOT_DOCUMENT_AUTHOR(HttpStatus.FORBIDDEN, "NOT_DOCUMENT_AUTHOR", "작성자나 팀장만 지울 수 있어요"),
    // TODO(확인 필요): 기능정의서 문구 미정 (NET-04, NET-05)
    NOT_CHAT_ROOM_MEMBER(HttpStatus.FORBIDDEN, "NOT_CHAT_ROOM_MEMBER", "채팅방 참여자만 접근할 수 있습니다."),

    // 404 Not Found
    // PST-004 ⑤ 문구
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "POST_NOT_FOUND", "삭제되었거나 없는 모집글이에요"),
    // REC-002 ⑥ 문구 (11/13 추가)
    APPLICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "APPLICATION_NOT_FOUND", "이미 취소되었거나 없는 지원이에요"),
    // BRD-003 ⑤ 문구
    BOARD_POST_NOT_FOUND(HttpStatus.NOT_FOUND, "BOARD_POST_NOT_FOUND", "삭제되었거나 없는 글이에요"),
    // BRD-006 ④ 문구
    BOARD_POST_IMAGE_NOT_FOUND(
            HttpStatus.NOT_FOUND, "BOARD_POST_IMAGE_NOT_FOUND", "삭제되었거나 없는 사진이에요"),
    // PST-007 ④ 문구
    POST_IMAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "POST_IMAGE_NOT_FOUND", "삭제되었거나 없는 사진이에요"),
    // TODO(확인 필요): 기능정의서에 문구가 없어 임시로 정함 (API 명세서 MAIN-02)
    CONTEST_NOT_FOUND(HttpStatus.NOT_FOUND, "CONTEST_NOT_FOUND", "존재하지 않는 공모전입니다."),
    // TODO(확인 필요): 기능정의서 문구 미정 (NET-05)
    CHAT_ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "CHAT_ROOM_NOT_FOUND", "존재하지 않는 채팅방입니다."),

    // ---- 정진우 404 ----
    // AUTH-01 ②, AUTH-02 ⑥ 문구
    EMAIL_NOT_FOUND(HttpStatus.NOT_FOUND, "EMAIL_NOT_FOUND", "가입되지 않은 이메일이에요"),
    // AUTH-02 ① 문구 (공개 프로필 404도 이 코드)
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "MEMBER_NOT_FOUND", "입력한 닉네임으로 가입된 계정이 없어요"),
    // TODO(확인 필요): API 명세서에 「404」만 있고 코드가 없어 임시로 정함 (MY-07·08·09, NOTI-01·07)
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "없거나 삭제된 항목이에요"),

    // ---- 정진우 409 ----
    // AUTH-05 ⑥ 문구
    EMAIL_DUPLICATED(HttpStatus.CONFLICT, "EMAIL_DUPLICATED", "이미 가입된 이메일이에요"),
    // AUTH-05 ⑥: 카카오·구글로 가입된 이메일로 이메일 가입을 시도 → 그 방식으로 로그인 안내
    SOCIAL_EMAIL_REGISTERED(HttpStatus.CONFLICT, "SOCIAL_EMAIL_REGISTERED", "간편 로그인으로 가입된 이메일이에요"),
    // TODO(확인 필요): 기능정의서에 문구가 없어 임시로 정함 (AUTH-06 ③)
    NICKNAME_DUPLICATED(HttpStatus.CONFLICT, "NICKNAME_DUPLICATED", "이미 쓰고 있는 닉네임이에요"),
    // AUTH-05 ③: 5번 틀리면 그 번호는 무효 (문구는 임시)
    CODE_LOCKED(HttpStatus.CONFLICT, "CODE_LOCKED", "인증번호를 5번 틀렸어요. 인증번호를 다시 받아 주세요"),
    // AUTH-01 ⑤, MY-11 ⑤ 문구 (남은 날은 메시지에 넣는다)
    ACCOUNT_WITHDRAWN(HttpStatus.CONFLICT, "ACCOUNT_WITHDRAWN", "탈퇴 신청한 계정이에요"),
    // MY-11 ③ 문구
    LEADER_CANNOT_WITHDRAW(HttpStatus.CONFLICT, "LEADER_CANNOT_WITHDRAW", "팀장인 채팅방을 먼저 정리해 주세요"),
    // TODO(확인 필요): 기능정의서에 문구가 없어 임시로 정함 (MY-07 ④)
    CAREER_LIMIT(HttpStatus.CONFLICT, "CAREER_LIMIT", "경력은 20개까지 넣을 수 있어요"),
    // TODO(확인 필요): 기능정의서에 문구가 없어 임시로 정함 (MY-08 ③)
    ALREADY_PENDING(HttpStatus.CONFLICT, "ALREADY_PENDING", "이미 인증을 확인하고 있어요"),

    // ---- 정진우 410 ----
    // TODO(확인 필요): 기능정의서에 문구가 없어 임시로 정함 (MY-11 ①)
    RESTORE_EXPIRED(HttpStatus.GONE, "RESTORE_EXPIRED", "복원 기한(30일)이 지나 복원할 수 없어요"),

    // ---- 정진우 503 ----
    // AUTH-05 ④ 문구
    MAIL_SEND_FAILED(
            HttpStatus.SERVICE_UNAVAILABLE, "MAIL_SEND_FAILED", "메일을 보내지 못했어요. 잠시 후 다시 시도해 주세요"),

    // 409 Conflict
    ALREADY_APPLIED(HttpStatus.CONFLICT, "ALREADY_APPLIED", "이미 지원한 모집글입니다."),
    POST_FULL(HttpStatus.CONFLICT, "POST_FULL", "모집 인원이 모두 충원되었습니다."),
    DOCUMENT_LIMIT(HttpStatus.CONFLICT, "DOCUMENT_LIMIT", "문서 개수 제한을 초과했습니다."),
    // REC-001 ③, REC-006 ③ 문구
    POST_COMPLETED(HttpStatus.CONFLICT, "POST_COMPLETED", "모집이 완료된 게시글입니다"),
    // PST-007 ② 문구
    POST_IMAGE_LIMIT(HttpStatus.CONFLICT, "POST_IMAGE_LIMIT", "사진은 3장까지 올릴 수 있어요"),
    // BRD-006 ② 문구 (PST-007 ②와 같음)
    BOARD_POST_IMAGE_LIMIT(HttpStatus.CONFLICT, "BOARD_POST_IMAGE_LIMIT", "사진은 3장까지 올릴 수 있어요"),
    // REC-001 ③ 문구 (11/10 김수형 결정, 제안값)
    RECRUIT_PERIOD_ENDED(HttpStatus.CONFLICT, "RECRUIT_PERIOD_ENDED", "모집 기간이 종료된 게시글입니다"),
    // REC-001 ③ 문구
    ALREADY_TEAM_MEMBER(HttpStatus.CONFLICT, "ALREADY_TEAM_MEMBER", "이미 이 팀의 팀원이에요"),
    // REC-002 ④ 문구
    ALREADY_PROCESSED(HttpStatus.CONFLICT, "ALREADY_PROCESSED", "이미 처리된 지원이에요"),
    // TODO(확인 필요): 기능정의서에 문구가 없어 임시로 정함 (NTC-05, 고정 동시 요청으로 팀당 고정 1개 UNIQUE 위반)
    NOTICE_PIN_CONFLICT(
            HttpStatus.CONFLICT, "NOTICE_PIN_CONFLICT", "다른 공지가 방금 고정됐어요. 새로고침 후 다시 시도해 주세요"),

    // 429 Too Many Requests
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS", "요청 횟수를 초과했습니다."),

    // 500 Internal Server Error
    INTERNAL_SERVER_ERROR(
            HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
