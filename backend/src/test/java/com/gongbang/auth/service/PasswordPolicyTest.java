package com.gongbang.auth.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gongbang.global.error.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PasswordPolicyTest {
    @ParameterizedTest
    @ValueSource(strings = {"abcd1234가", "abcd1234!한", "abcd1234!ㄱ", "abcd1234!ᄀ"})
    void rejectsHangulIncludingJamo(String password) {
        assertThatThrownBy(() -> PasswordPolicy.validate(password))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void acceptsExistingEnglishNumberSymbolPolicy() {
        assertThatCode(() -> PasswordPolicy.validate("abcd1234!")).doesNotThrowAnyException();
    }
}
