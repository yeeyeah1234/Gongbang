package com.gongbang.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CertificateRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 100) String issuer,
        @NotNull LocalDate acquiredDate,
        @Size(max = 50) String certNumber,
        Long proofFileId) {}
