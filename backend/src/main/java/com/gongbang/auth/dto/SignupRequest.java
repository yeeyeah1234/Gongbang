package com.gongbang.auth.dto;

import com.gongbang.member.domain.DesiredRole;
import com.gongbang.member.domain.Grade;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/** API 명세서 AUTH-04·07 POST /api/auth/signup. 이력은 가입 뒤 MY API로 따로 저장한다. */
public record SignupRequest(
        @NotBlank String email,
        @NotBlank String verificationToken,
        @NotBlank String password,
        @NotBlank String nickname,
        @NotEmpty List<@Valid TermsItem> terms,
        @NotEmpty List<String> interestCategoryCodes,
        List<DesiredRole> desiredRoles,
        @Size(max = 50) String school,
        @Size(max = 50) String major,
        Grade grade,
        @Size(max = 50) String bio) {}
