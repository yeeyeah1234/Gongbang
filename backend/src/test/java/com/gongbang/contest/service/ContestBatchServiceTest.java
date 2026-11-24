package com.gongbang.contest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.gongbang.contest.domain.Category;
import com.gongbang.contest.domain.Contest;
import com.gongbang.contest.domain.ContestDeadlineChangedEvent;
import com.gongbang.contest.dto.ContestBatchItem;
import com.gongbang.contest.dto.ContestBatchResponse;
import com.gongbang.contest.repository.CategoryRepository;
import com.gongbang.contest.repository.ContestRepository;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ContestBatchServiceTest {

    @Mock private ContestRepository contestRepository;

    @Mock private CategoryRepository categoryRepository;

    @Mock private ApplicationEventPublisher eventPublisher;

    private ContestBatchService contestBatchService;

    private final Category planningCategory = mock(Category.class);

    @BeforeEach
    void setUp() {
        contestBatchService =
                new ContestBatchService(contestRepository, categoryRepository, eventPublisher);
    }

    private ContestBatchItem item(String sourceUrl, LocalDate deadline) {
        return itemWithHomepage(sourceUrl, deadline, null);
    }

    private ContestBatchItem itemWithHomepage(
            String sourceUrl, LocalDate deadline, String homepageUrl) {
        return new ContestBatchItem(
                sourceUrl,
                "WEVITY",
                "제목",
                "주최",
                "PLANNING",
                null,
                null,
                deadline,
                "설명",
                homepageUrl);
    }

    private Contest savedContest() {
        ArgumentCaptor<Contest> captor = ArgumentCaptor.forClass(Contest.class);
        verify(contestRepository).save(captor.capture());
        return captor.getValue();
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
        verify(eventPublisher, never()).publishEvent(any());
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
    @DisplayName("이미 있는 sourceUrl이고 마감일이 그대로면 updated만 올리고 이벤트는 발행하지 않는다")
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
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("이미 있는 sourceUrl인데 마감일이 바뀌면 deadlineChanged를 올리고 새 마감일로 이벤트를 발행한다")
    void 마감일이_바뀌면_deadlineChanged를_올린다() {
        given(categoryRepository.findByCode("PLANNING")).willReturn(Optional.of(planningCategory));
        Contest existing =
                Contest.create("url-3", planningCategory, item("url-3", LocalDate.of(2026, 12, 6)));
        ReflectionTestUtils.setField(existing, "id", 3L);
        given(contestRepository.findBySourceUrl("url-3")).willReturn(Optional.of(existing));

        ContestBatchResponse result =
                contestBatchService.upsert(List.of(item("url-3", LocalDate.of(2026, 12, 20))));

        assertThat(result.created()).isZero();
        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.deadlineChanged()).isEqualTo(1);
        assertThat(existing.getEndDate()).isEqualTo(LocalDate.of(2026, 12, 20));
        verify(eventPublisher)
                .publishEvent(new ContestDeadlineChangedEvent(3L, LocalDate.of(2026, 12, 20)));
    }

    @Test
    @DisplayName("홈페이지 주소는 공통 URL 정책으로 앞뒤 공백을 지우고 대문자 스킴도 받는다 (#120)")
    void 홈페이지_주소는_공통_정책으로_정규화한다() {
        given(categoryRepository.findByCode("PLANNING")).willReturn(Optional.of(planningCategory));
        given(contestRepository.findBySourceUrl("url-4")).willReturn(Optional.empty());

        contestBatchService.upsert(
                List.of(
                        itemWithHomepage(
                                "url-4",
                                LocalDate.of(2026, 12, 6),
                                "  HTTPS://Example.org/Path  ")));

        assertThat(savedContest().getHomepageUrl()).isEqualTo("HTTPS://Example.org/Path");
    }

    @Test
    @DisplayName("홈페이지 주소가 비어 있으면 null로 저장한다 (#120)")
    void 빈_홈페이지_주소는_null로_저장한다() {
        given(categoryRepository.findByCode("PLANNING")).willReturn(Optional.of(planningCategory));
        given(contestRepository.findBySourceUrl("url-5")).willReturn(Optional.empty());

        contestBatchService.upsert(
                List.of(itemWithHomepage("url-5", LocalDate.of(2026, 12, 6), "   ")));

        assertThat(savedContest().getHomepageUrl()).isNull();
    }

    @Test
    @DisplayName("홈페이지 주소가 http(s)가 아니거나 호스트가 없으면 INVALID_INPUT이고 저장하지 않는다 (#120)")
    void 잘못된_홈페이지_주소는_예외() {
        for (String bad : List.of("javascript:alert(1)", "www.example.org", "https://")) {
            ContestBatchItem item = itemWithHomepage("url-6", LocalDate.of(2026, 12, 6), bad);

            BusinessException ex =
                    assertThrows(
                            BusinessException.class,
                            () -> contestBatchService.upsert(List.of(item)));

            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
        }
        verify(contestRepository, never()).save(any());
    }
}
