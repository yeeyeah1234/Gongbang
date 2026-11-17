package com.gongbang.contest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.gongbang.contest.repository.CategoryRepository;
import com.gongbang.contest.repository.ContestRepository;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContestLookupServiceTest {

    @Mock private ContestRepository contestRepository;
    @Mock private CategoryRepository categoryRepository;

    private ContestLookupService contestLookupService;

    @BeforeEach
    void setUp() {
        contestLookupService = new ContestLookupService(contestRepository, categoryRepository);
    }

    @Test
    @DisplayName("찾을 ID가 없으면 DB를 읽지 않고 빈 결과를 돌려준다")
    void 빈_ID_목록이면_조회하지_않는다() {
        assertThat(contestLookupService.findSummaries(Set.of())).isEmpty();
        assertThat(contestLookupService.findCategoryNames(Set.of())).isEmpty();

        verify(contestRepository, never()).findAllWithCategoryByIdIn(any());
        verify(categoryRepository, never()).findAllById(any());
    }
}
