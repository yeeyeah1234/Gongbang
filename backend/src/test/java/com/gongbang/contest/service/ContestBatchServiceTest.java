package com.gongbang.contest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.gongbang.contest.domain.Category;
import com.gongbang.contest.domain.Contest;
import com.gongbang.contest.dto.ContestBatchItem;
import com.gongbang.contest.dto.ContestBatchResponse;
import com.gongbang.contest.repository.CategoryRepository;
import com.gongbang.contest.repository.ContestRepository;
import com.gongbang.global.error.exception.BusinessException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContestBatchServiceTest {

    @Mock private ContestRepository contestRepository;

    @Mock private CategoryRepository categoryRepository;

    private ContestBatchService contestBatchService;

    private final Category planningCategory = mock(Category.class);

    @BeforeEach
    void setUp() {
        contestBatchService = new ContestBatchService(contestRepository, categoryRepository);
    }

    private ContestBatchItem item(String sourceUrl, LocalDate deadline) {
        return new ContestBatchItem(
                sourceUrl, "WEVITY", "제목", "주최", "PLANNING", null, null, deadline, "설명", null);
    }

    @Test
    @DisplayName("처음 보는 sourceUrl이면 새로 생성하고 created를 올린다")
    void 신규_공모전은_생성한다() {
        given(categoryRepository.findByCode("PLANNING")).willReturn(Optional.of(planningCategory));
        given(contestRepository.findBySourceUrl("url-1")).willReturn(Optional.empty());

        ContestBatchResponse result =
                contestBatchService.upsert(List.of(item("url-1", LocalDate.of(2026, 12, 6))));

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.updated()).isZero();
        assertThat(result.deadlineChanged()).isZero();
        verify(contestRepository).save(any(Contest.class));
    }

    @Test
    @DisplayName("존재하지 않는 categoryCode면 INVALID_INPUT 예외를 던진다")
    void 모르는_분야코드는_예외() {
        given(categoryRepository.findByCode("UNKNOWN")).willReturn(Optional.empty());
        ContestBatchItem item =
                new ContestBatchItem(
                        "url-x",
                        "WEVITY",
                        "제목",
                        "주최",
                        "UNKNOWN",
                        null,
                        null,
                        LocalDate.of(2026, 12, 6),
                        "설명",
                        null);

        assertThrows(BusinessException.class, () -> contestBatchService.upsert(List.of(item)));
    }

    @Test
    @DisplayName("이미 있는 sourceUrl이고 마감일이 그대로면 updated만 올린다")
    void 마감일이_그대로면_deadlineChanged는_올리지_않는다() {
        given(categoryRepository.findByCode("PLANNING")).willReturn(Optional.of(planningCategory));
        Contest existing =
                Contest.create("url-2", planningCategory, item("url-2", LocalDate.of(2026, 12, 6)));
        given(contestRepository.findBySourceUrl("url-2")).willReturn(Optional.of(existing));

        ContestBatchResponse result =
                contestBatchService.upsert(List.of(item("url-2", LocalDate.of(2026, 12, 6))));

        assertThat(result.created()).isZero();
        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.deadlineChanged()).isZero();
    }

    @Test
    @DisplayName("이미 있는 sourceUrl인데 마감일이 바뀌면 deadlineChanged도 올린다")
    void 마감일이_바뀌면_deadlineChanged를_올린다() {
        given(categoryRepository.findByCode("PLANNING")).willReturn(Optional.of(planningCategory));
        Contest existing =
                Contest.create("url-3", planningCategory, item("url-3", LocalDate.of(2026, 12, 6)));
        given(contestRepository.findBySourceUrl("url-3")).willReturn(Optional.of(existing));

        ContestBatchResponse result =
                contestBatchService.upsert(List.of(item("url-3", LocalDate.of(2026, 12, 20))));

        assertThat(result.created()).isZero();
        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.deadlineChanged()).isEqualTo(1);
        assertThat(existing.getEndDate()).isEqualTo(LocalDate.of(2026, 12, 20));
    }
}
