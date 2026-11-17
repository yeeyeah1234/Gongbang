package com.gongbang.member.dto;

import jakarta.validation.constraints.NotBlank;

/** MY-11 ②: 「탈퇴합니다」 직접 입력 + 비밀번호 (카카오 계정은 비밀번호 없음) */
public record WithdrawRequest(String password, @NotBlank String confirmText) {}
