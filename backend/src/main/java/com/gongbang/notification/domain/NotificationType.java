package com.gongbang.notification.domain;

/**
 * notification.type (DB 명세서 비고). 종류마다 수신 설정 묶음, 메일 방식, 화면 분류(NOTI-01 ② 필터)를 정한다. settingType이
 * null이면 끌 수 없는 알림(운영 공지·환영·인증 결과).
 */
public enum NotificationType {
    APPLY_NEW(SettingType.APPLY_NEW, SendMode.IMMEDIATE, Category.TEAM_BUILDING),
    APPLY_ACCEPTED(SettingType.APPLY_RESULT, SendMode.IMMEDIATE, Category.TEAM_BUILDING),
    APPLY_REJECTED(SettingType.APPLY_RESULT, SendMode.IMMEDIATE, Category.TEAM_BUILDING),
    POST_DELETED(SettingType.POST_DELETED, SendMode.IMMEDIATE, Category.TEAM_BUILDING),
    COMMENT(SettingType.COMMENT, SendMode.HOURLY_DIGEST, Category.COMMENT),
    REPLY(SettingType.COMMENT, SendMode.HOURLY_DIGEST, Category.COMMENT),
    TEAM_INVITE(SettingType.TEAM_INVITE, SendMode.IMMEDIATE, Category.TEAM_BUILDING),
    // NOTI-06 ④ 일정 알림은 메일 기본 꺼짐 → 메일 없음(null)
    EVENT_REMIND(SettingType.WORKSPACE, null, Category.WORKSPACE),
    NOTICE_NEW(SettingType.WORKSPACE, SendMode.IMMEDIATE, Category.WORKSPACE),
    TEAM_DEADLINE(SettingType.WORKSPACE, SendMode.IMMEDIATE, Category.DEADLINE),
    BOOKMARK_DEADLINE(SettingType.BOOKMARK_DEADLINE, SendMode.DAILY_9AM, Category.DEADLINE),
    // MY-08 ⑦: 인증 결과는 사이트 알림과 이메일로 전달한다.
    VERIFY_RESULT(null, SendMode.IMMEDIATE, Category.NOTICE),
    // 일반 운영 공지는 사이트만, 정책 변경 메일은 관리자 선택으로 별도 등록한다.
    SERVICE_NOTICE(null, null, Category.NOTICE),
    WELCOME(null, null, Category.NOTICE),
    NICKNAME_CHANGE_REQUIRED(null, SendMode.IMMEDIATE, Category.NOTICE),
    ACCOUNT_SANCTION(null, SendMode.IMMEDIATE, Category.NOTICE),
    ADMIN_INVITE(null, null, Category.NOTICE),
    ADMIN_GRANTED(null, null, Category.NOTICE),
    POLICY_NOTICE(null, SendMode.IMMEDIATE, Category.NOTICE),
    BOARD_POST_NEW(SettingType.COMMUNITY_NEW, null, Category.NOTICE);

    /** NOTI-01 ② 종류 필터: 댓글, 팀 빌딩(지원·초대), 워크스페이스, 마감 임박, 공지 */
    public enum Category {
        COMMENT,
        TEAM_BUILDING,
        WORKSPACE,
        DEADLINE,
        NOTICE
    }

    private final SettingType settingType;
    private final SendMode sendMode;
    private final Category category;

    NotificationType(SettingType settingType, SendMode sendMode, Category category) {
        this.settingType = settingType;
        this.sendMode = sendMode;
        this.category = category;
    }

    public SettingType getSettingType() {
        return settingType;
    }

    public SendMode getSendMode() {
        return sendMode;
    }

    public Category getCategory() {
        return category;
    }
}
