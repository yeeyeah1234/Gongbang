package com.gongbang.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** MY-08 ⑤ 인증 요청 때만 실명을 받는다 (공개 안 됨) */
public record VerifyRequest(@NotBlank @Size(max = 30) String realName, Long proofFileId) {}
