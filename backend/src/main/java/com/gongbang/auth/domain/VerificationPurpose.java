package com.gongbang.auth.domain;

/** email_verification.purpose: SIGNUP / LOGIN(5회 실패 추가 인증) / RESET / RESTORE */
public enum VerificationPurpose {
    SIGNUP(30),
    LOGIN(10),
    RESET(10),
    RESTORE(10);

    /** 인증을 끝낸 뒤 이 시간(분) 안에 다음 단계를 끝내야 한다 (AUTH-05 ⑤ 가입 30분, AUTH-02 ③ 재설정 10분) */
    private final int validMinutesAfterVerify;

    VerificationPurpose(int validMinutesAfterVerify) {
        this.validMinutesAfterVerify = validMinutesAfterVerify;
    }

    public int getValidMinutesAfterVerify() {
        return validMinutesAfterVerify;
    }
}
