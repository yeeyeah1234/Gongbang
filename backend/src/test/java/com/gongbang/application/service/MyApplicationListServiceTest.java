package com.gongbang.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gongbang.application.repository.ApplicationRepository;
import com.gongbang.global.error.exception.BusinessException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class MyApplicationListServiceTest {
    private final ApplicationRepository repository = mock(ApplicationRepository.class);
    private final MyApplicationListService service = new MyApplicationListService(repository);

    @Test
    void anonymousCannotRead() {
        assertThatThrownBy(() -> service.list(null, 0, 10)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void invalidPaginationCannotQuery() {
        assertThatThrownBy(() -> service.list(7L, -1, 10)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.list(7L, 0, 0)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.list(7L, 0, 101)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void queryUsesCurrentMemberAndPreservesTotal() {
        var pageable = PageRequest.of(1, 10);
        when(repository.findMine(7L, pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 31));
        var response = service.list(7L, 1, 10);
        verify(repository).findMine(7L, pageable);
        assertThat(response.totalCount()).isEqualTo(31);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.page()).isEqualTo(1);
    }
}
