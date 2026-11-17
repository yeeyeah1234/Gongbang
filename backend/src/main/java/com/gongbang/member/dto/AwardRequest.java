package com.gongbang.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record AwardRequest(
        @NotBlank @Size(max = 100) String contestName,
        @NotBlank @Size(max = 50) String awardName,
        @NotNull LocalDate awardedDate,
        Long proofFileId) {}
