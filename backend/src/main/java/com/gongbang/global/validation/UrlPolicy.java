package com.gongbang.global.validation;

import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import java.net.URI;
import java.net.URISyntaxException;

/** 공통 URL 입력 정책 (#120). 스킴은 대소문자 무관 HTTP/HTTPS, 호스트 필수, 빈 값은 null. */
public final class UrlPolicy {

    private UrlPolicy() {}

    public static String validateOrNull(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String trimmed = url.strip();
        try {
            URI parsed = new URI(trimmed);
            if (("http".equalsIgnoreCase(parsed.getScheme())
                            || "https".equalsIgnoreCase(parsed.getScheme()))
                    && parsed.getHost() != null
                    && !parsed.getHost().isBlank()) {
                return trimmed;
            }
        } catch (URISyntaxException ignored) {
            // 잘못된 URI 문법도 동일한 입력 오류로 응답한다.
        }
        throw new BusinessException(
                ErrorCode.INVALID_INPUT, "호스트가 포함된 http:// 또는 https:// 주소를 넣어 주세요");
    }
}
