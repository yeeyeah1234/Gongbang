package com.gongbang.auth.dto;

import com.gongbang.member.domain.TermsType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TermsItem(@NotNull TermsType type, @NotBlank String version, boolean agreed) {}
