package com.gongbang.contest.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CrawlerKeyFilterTest {

    private static final String VALID_KEY = "test-crawler-key";

    @Mock private FilterChain filterChain;

    private CrawlerKeyFilter crawlerKeyFilter;

    @BeforeEach
    void setUp() {
        crawlerKeyFilter = new CrawlerKeyFilter(new ObjectMapper());
        ReflectionTestUtils.setField(crawlerKeyFilter, "crawlerInternalKey", VALID_KEY);
    }

    @Test
    @DisplayName("internal/contests 경로가 아니면 필터를 거치지 않는다")
    void internal_경로가_아니면_통과() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/contests");

        assertThat(crawlerKeyFilter.shouldNotFilter(request)).isTrue();
    }

    @Test
    @DisplayName("키 헤더가 없으면 401 INVALID_INTERNAL_KEY를 응답하고 체인을 타지 않는다")
    void 키가_없으면_401() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/internal/contests/batch");
        MockHttpServletResponse response = new MockHttpServletResponse();

        crawlerKeyFilter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("INVALID_INTERNAL_KEY");
        verifyNoInteractions(filterChain);
    }

    @Test
    @DisplayName("다른 내부 키(예: 동기화 서버 키)로는 401 INVALID_INTERNAL_KEY")
    void 다른_내부_키로는_401() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/internal/contests/batch");
        request.addHeader("X-Crawler-Key", "sync-server-key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        crawlerKeyFilter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("INVALID_INTERNAL_KEY");
        verifyNoInteractions(filterChain);
    }

    @Test
    @DisplayName("서버에 크롤러 키가 설정되지 않았으면 어떤 키로도 401 (fail-closed)")
    void 설정값이_비어_있으면_401() throws Exception {
        ReflectionTestUtils.setField(crawlerKeyFilter, "crawlerInternalKey", "");
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/internal/contests/batch");
        request.addHeader("X-Crawler-Key", "");
        MockHttpServletResponse response = new MockHttpServletResponse();

        crawlerKeyFilter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("INVALID_INTERNAL_KEY");
        verifyNoInteractions(filterChain);
    }

    @Test
    @DisplayName("키의 앞부분만 맞으면 401")
    void 키_앞부분만_맞으면_401() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/internal/contests/batch");
        request.addHeader("X-Crawler-Key", VALID_KEY.substring(0, VALID_KEY.length() - 1));
        MockHttpServletResponse response = new MockHttpServletResponse();

        crawlerKeyFilter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(filterChain);
    }

    @Test
    @DisplayName("키가 맞으면 체인으로 넘긴다")
    void 키가_맞으면_통과() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/internal/contests/batch");
        request.addHeader("X-Crawler-Key", VALID_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();

        crawlerKeyFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }
}
