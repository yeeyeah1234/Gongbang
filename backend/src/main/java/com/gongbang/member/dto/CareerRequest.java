package com.gongbang.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** MY-07 ②: endDate가 null이면 [진행 중] */
public record CareerRequest(
        @NotBlank @Size(max = 50) String organization,
        @NotBlank @Size(max = 50) String role,
        @NotNull LocalDate startDate,
        LocalDate endDate,
        @Size(max = 200) String description) {}
