package com.gongbang.notification.domain;

/**
 * notification_setting.setting_type (MY-10 ①). 보안·운영 메일은 끌 수 없어 없다.
 *
 * <p>defaultEmail: NOTI-06 ④ 일정 알림 이메일만 기본 꺼짐 — WORKSPACE에 일정이 들어 있어 EVENT_REMIND는
 * NotificationService에서 따로 끈다.
 */
public enum SettingType {
    APPLY_RESULT,
    APPLY_NEW,
    TEAM_INVITE,
    POST_DELETED,
    COMMENT,
    WORKSPACE,
    BOOKMARK_DEADLINE
}
