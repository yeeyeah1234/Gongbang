package com.gongbang.global.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class UrlPolicyTest {
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n", "\u2003"})
    void 빈_입력은_null로_정규화한다(String value) {
        assertThat(UrlPolicy.validateOrNull(value)).isNull();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "http://example.com",
                "https://example.com",
                "HTTPS://Example.COM/Path?Key=Value#Section",
                "hTtP://example.com/path",
                "https://example.com:8443/a%20b?q=x%2Fy",
                "http://localhost:8080",
                "http://127.0.0.1",
                "https://[::1]/",
                "https://xn--3e0b707e.com/"
            })
    void HTTP스킴과_호스트를_검증하고_주소를_보존한다(String value) {
        assertThat(UrlPolicy.validateOrNull(value)).isEqualTo(value);
    }

    @Test
    void 앞뒤_공백만_제거하고_경로와_쿼리는_보존한다() {
        assertThat(UrlPolicy.validateOrNull("\u2003 HTTPS://Example.com/A?Token=AbC%2F#Part \t"))
                .isEqualTo("HTTPS://Example.com/A?Token=AbC%2F#Part");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "https://",
                "http://",
                "https:///path",
                "http:example.com",
                "//example.com",
                "/relative",
                "example.com",
                "ftp://example.com",
                "javascript:alert(1)",
                "data:text/html,test",
                "https://?q=test",
                "https://#fragment",
                "https://exa mple.com",
                "https://example.com/a b",
                "https://example.com/%ZZ",
                "https://example.com\n/path",
                "https://example.com:abc",
                "https://[::1"
            })
    void 잘못된_스킴_호스트_문법은_입력오류로_거절한다(String value) {
        assertThatThrownBy(() -> UrlPolicy.validateOrNull(value))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }
}
