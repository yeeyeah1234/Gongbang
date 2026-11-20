package com.gongbang.auth.service;

import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import java.util.regex.Pattern;

/** AUTH-06 ①: 8~20자, 영문·숫자·특수문자 각 1개 이상 (AUTH-02 ④, MY-04 ② 같은 규칙) */
public final class PasswordPolicy {

    private static final Pattern LETTER = Pattern.compile("[A-Za-z]");
    private static final Pattern DIGIT = Pattern.compile("[0-9]");
    private static final Pattern SPECIAL = Pattern.compile("[^A-Za-z0-9\\s]");
    private static final Pattern HANGUL = Pattern.compile("\\p{IsHangul}");

    private PasswordPolicy() {}

    public static void validate(String password) {
        if (password == null
                || password.length() < 8
                || password.length() > 20
                || HANGUL.matcher(password).find()
                || !LETTER.matcher(password).find()
                || !DIGIT.matcher(password).find()
                || !SPECIAL.matcher(password).find()) {
            throw new BusinessException(ErrorCode.PASSWORD_POLICY);
        }
    }
}
