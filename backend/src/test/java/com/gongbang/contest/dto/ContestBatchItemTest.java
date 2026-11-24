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
                "url-1",
                "WEVITY",
                "제목",
                "주최",
                "PLANNING",
                null,
                null,
                LocalDate.of(2026, 12, 6),
                "설명",
                homepageUrl);
    }

    @Test
    @DisplayName("홈페이지 주소는 없거나 500자 안이면 DTO 검증을 통과한다 (형식은 서비스의 UrlPolicy가 검사)")
    void 홈페이지_주소가_없거나_500자_안이면_통과() {
        assertThat(validator.validate(item(null))).isEmpty();
        assertThat(validator.validate(item("HTTPS://example.org/contest"))).isEmpty();
        assertThat(validator.validate(item("http://example.org"))).isEmpty();
    }

    @Test
    @DisplayName("홈페이지 주소가 500자를 넘으면 막는다")
    void 홈페이지_주소가_500자를_넘으면_실패() {
        assertThat(validator.validate(item("https://example.org/" + "a".repeat(481)))).isNotEmpty();
    }

    @Test
    @DisplayName("withHomepageUrl은 홈페이지 주소만 바꾸고 나머지는 그대로 둔다")
    void withHomepageUrl은_주소만_바꾼다() {
        ContestBatchItem original = item("  https://example.org  ");

        ContestBatchItem changed = original.withHomepageUrl("https://example.org");

        assertThat(changed.homepageUrl()).isEqualTo("https://example.org");
        assertThat(changed.withHomepageUrl(original.homepageUrl())).isEqualTo(original);
    }
}
