package com.gongbang.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gongbang.global.error.exception.BusinessException;
import org.junit.jupiter.api.Test;

class AuthServiceTest {

    @Test
    void 이메일은_앞부분만_남기고_가린다() {
        assertThat(AuthService.maskEmail("jinwoo03@gmail.com")).isEqualTo("ji****@g****.com");
    }

    @Test
    void 비밀번호는_영문_숫자_특수문자가_모두_있어야_한다() {
        PasswordPolicy.validate("abcd1234!");
        assertThatThrownBy(() -> PasswordPolicy.validate("abcd1234"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> PasswordPolicy.validate("a1!"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> PasswordPolicy.validate("abcdefghij1234567890!"))
                .isInstanceOf(BusinessException.class);
    }
}
