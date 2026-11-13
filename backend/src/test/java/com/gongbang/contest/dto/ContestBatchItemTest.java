package com.gongbang.contest.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ContestBatchItemTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private ContestBatchItem item(String homepageUrl) {
        return new ContestBatchItem(
                "url-1", "WEVITY", "제목", "주최", "PLANNING", null, null, LocalDate.of(2026, 12, 6), "설명",
                homepageUrl);
    }

    @Test
    @DisplayName("홈페이지 주소는 없거나 http(s)면 통과한다")
    void 홈페이지_주소가_없거나_http면_통과() {
        assertThat(validator.validate(item(null))).isEmpty();
        assertThat(validator.validate(item("https://example.org/contest"))).isEmpty();
        assertThat(validator.validate(item("http://example.org"))).isEmpty();
    }

    @Test
    @DisplayName("홈페이지 주소가 http(s)가 아니면 막는다 (javascript: 차단)")
    void 홈페이지_주소가_http가_아니면_실패() {
        assertThat(validator.validate(item("javascript:alert(1)"))).isNotEmpty();
        assertThat(validator.validate(item("www.example.org"))).isNotEmpty();
    }
}
