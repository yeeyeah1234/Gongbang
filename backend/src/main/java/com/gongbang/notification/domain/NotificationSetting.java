package com.gongbang.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** MY-10 알림 수신 설정. 행이 없으면 기본값(사이트·이메일 모두 켬). */
@Entity
@Table(name = "notification_setting")
@IdClass(NotificationSetting.Key.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationSetting {

    @Id
    @Column(name = "member_id")
    private Long memberId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "setting_type", length = 20)
    private SettingType settingType;

    @Column(name = "site_enabled", nullable = false)
    private boolean siteEnabled = true;

    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled = true;

    public NotificationSetting(Long memberId, SettingType settingType) {
        this.memberId = memberId;
        this.settingType = settingType;
    }

    public void change(boolean siteEnabled, boolean emailEnabled) {
        this.siteEnabled = siteEnabled;
        this.emailEnabled = emailEnabled;
    }

    @Getter
    @NoArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        private Long memberId;
        private SettingType settingType;

        public Key(Long memberId, SettingType settingType) {
            this.memberId = memberId;
            this.settingType = settingType;
        }
    }
}
