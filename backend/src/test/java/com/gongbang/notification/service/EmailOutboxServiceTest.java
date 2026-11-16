package com.gongbang.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class EmailOutboxServiceTest {

    @Test
    void 밤_10시부터_오전_8시까지_생긴_메일은_오전_8시에_보낸다() {
        assertThat(EmailOutboxService.avoidNight(LocalDateTime.of(2026, 11, 16, 23, 10)))
                .isEqualTo(LocalDateTime.of(2026, 11, 17, 8, 0));
        assertThat(EmailOutboxService.avoidNight(LocalDateTime.of(2026, 11, 16, 3, 0)))
                .isEqualTo(LocalDateTime.of(2026, 11, 16, 8, 0));
        assertThat(EmailOutboxService.avoidNight(LocalDateTime.of(2026, 11, 16, 14, 0)))
                .isEqualTo(LocalDateTime.of(2026, 11, 16, 14, 0));
    }
}
