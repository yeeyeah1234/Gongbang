package com.gongbang.member.service;

import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;

/** AGENTS 9: 사용자가 넣은 URL은 http:// 또는 https://만 받는다 */
public final class UrlPolicy {

    private UrlPolicy() {}

    public static String validateOrNull(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String trimmed = url.trim();
        String lower = trimmed.toLowerCase();
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT, "http:// 또는 https://로 시작하는 주소를 넣어 주세요");
        }
        return trimmed;
    }
}
