package com.gongbang.notification.service;

import com.gongbang.notification.domain.NotificationSetting;
import com.gongbang.notification.domain.SettingType;
import com.gongbang.notification.dto.NotificationSettingItem;
import com.gongbang.notification.repository.NotificationSettingRepository;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** MY-10 알림 수신 설정 (행이 없으면 기본값: 모두 켬) */
@Service
@RequiredArgsConstructor
public class NotificationSettingService {

    private final NotificationSettingRepository settingRepository;

    @Transactional(readOnly = true)
    public List<NotificationSettingItem> get(Long memberId) {
        Map<SettingType, NotificationSetting> saved = new EnumMap<>(SettingType.class);
        for (NotificationSetting s : settingRepository.findByMemberId(memberId)) {
            saved.put(s.getSettingType(), s);
        }
        List<NotificationSettingItem> items = new ArrayList<>();
        for (SettingType type : SettingType.values()) {
            NotificationSetting s = saved.get(type);
            items.add(
                    new NotificationSettingItem(
                            type,
                            s == null ? type.defaultSiteEnabled() : s.isSiteEnabled(),
                            type.supportsEmail() && (s == null || s.isEmailEnabled())));
        }
        return items;
    }

    /** ④ 바꾸는 즉시 저장. 보낸 종류만 바꾼다 */
    @Transactional
    public List<NotificationSettingItem> update(
            Long memberId, List<NotificationSettingItem> settings) {
        for (NotificationSettingItem item : settings) {
            NotificationSetting setting =
                    settingRepository
                            .findById(new NotificationSetting.Key(memberId, item.type()))
                            .orElseGet(
                                    () ->
                                            settingRepository.save(
                                                    new NotificationSetting(
                                                            memberId, item.type())));
            setting.change(item.siteEnabled(), item.emailEnabled());
        }
        return get(memberId);
    }
}
