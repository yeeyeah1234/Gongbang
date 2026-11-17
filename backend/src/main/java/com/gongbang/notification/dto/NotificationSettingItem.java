package com.gongbang.notification.dto;

import com.gongbang.notification.domain.SettingType;
import jakarta.validation.constraints.NotNull;

public record NotificationSettingItem(
        @NotNull SettingType type, boolean siteEnabled, boolean emailEnabled) {}
