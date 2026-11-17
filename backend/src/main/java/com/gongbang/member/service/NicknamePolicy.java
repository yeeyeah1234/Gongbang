package com.gongbang.member.service;

import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** AUTH-06 ③④: 2~10자 한글·영문·숫자, 금지어 불가 */
public final class NicknamePolicy {

    private static final Pattern ALLOWED = Pattern.compile("^[가-힣A-Za-z0-9]{2,10}$");

    /** TODO(확인 필요): 금지어 전체 목록은 팀 협의 (기능정의서에는 「운영자, 관리자, admin 등」) */
    private static final List<String> FORBIDDEN = List.of("운영자", "관리자", "admin", "공방", "탈퇴한사용자");

    private NicknamePolicy() {}

    public static String validate(String raw) {
        String nickname = raw == null ? "" : raw.trim();
        if (!ALLOWED.matcher(nickname).matches()) {
            throw new BusinessException(ErrorCode.NICKNAME_INVALID);
        }
        String lower = nickname.toLowerCase(Locale.ROOT);
        if (FORBIDDEN.stream().anyMatch(lower::contains)) {
            throw new BusinessException(ErrorCode.NICKNAME_FORBIDDEN);
        }
        return nickname;
    }
}
