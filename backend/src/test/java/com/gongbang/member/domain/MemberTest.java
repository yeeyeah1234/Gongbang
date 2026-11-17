package com.gongbang.member.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.service.NicknamePolicy;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class MemberTest {

    @Test
    void 다섯번_틀리면_추가_인증_열번이면_30분_잠금() {
        Member member = Member.createEmailMember("a@b.com", "hash", "진우");
        LocalDateTime now = LocalDateTime.of(2026, 11, 16, 12, 0);
        for (int i = 0; i < 5; i++) {
            member.increaseLoginFail(now);
        }
        assertThat(member.needsExtraVerification()).isTrue();
        member.passExtraVerification();
        assertThat(member.needsExtraVerification()).isFalse();
        for (int i = 0; i < 4; i++) {
            member.increaseLoginFail(now);
        }
        assertThat(member.isLocked(now)).isTrue();
        assertThat(member.isLocked(now.plusMinutes(31))).isFalse();
    }

    @Test
    void 로그인에_성공하면_틀린_횟수가_0이_된다() {
        Member member = Member.createEmailMember("a@b.com", "hash", "진우");
        member.increaseLoginFail(LocalDateTime.now());
        member.loggedIn(LocalDateTime.now());
        assertThat(member.getLoginFailCount()).isZero();
    }

    @Test
    void 탈퇴하면_30일_뒤가_복원_기한() {
        Member member = Member.createEmailMember("a@b.com", "hash", "진우");
        LocalDateTime now = LocalDateTime.of(2026, 10, 20, 10, 0);
        member.withdraw(now);
        assertThat(member.isWithdrawn()).isTrue();
        assertThat(member.restoreDeadline()).isEqualTo(now.plusDays(30));
        member.erasePersonalData();
        assertThat(member.getEmail()).isNull();
        assertThat(member.getNickname()).isNull();
    }

    @Test
    void 닉네임_규칙과_금지어() {
        assertThat(NicknamePolicy.validate(" 진우03 ")).isEqualTo("진우03");
        assertThatThrownBy(() -> NicknamePolicy.validate("a"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> NicknamePolicy.validate("관리자진우"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> NicknamePolicy.validate("Admin1"))
                .isInstanceOf(BusinessException.class);
    }
}
