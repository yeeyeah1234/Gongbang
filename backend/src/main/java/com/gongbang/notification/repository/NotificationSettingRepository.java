package com.gongbang.notification.repository;

import com.gongbang.notification.domain.NotificationSetting;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationSettingRepository
        extends JpaRepository<NotificationSetting, NotificationSetting.Key> {

    List<NotificationSetting> findByMemberId(Long memberId);
}
