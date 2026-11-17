package com.gongbang.notification.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record NotificationSettingsRequest(@NotNull List<@Valid NotificationSettingItem> settings) {}
