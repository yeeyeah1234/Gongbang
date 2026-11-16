package com.gongbang.member.dto;

import com.gongbang.member.domain.Career;
import java.time.LocalDate;

public record CareerResponse(
        Long careerId,
        String organization,
        String role,
        LocalDate startDate,
        LocalDate endDate,
        String description) {

    public static CareerResponse from(Career c) {
        return new CareerResponse(
                c.getId(),
                c.getOrganization(),
                c.getRole(),
                c.getStartDate(),
                c.getEndDate(),
                c.getDescription());
    }
}
