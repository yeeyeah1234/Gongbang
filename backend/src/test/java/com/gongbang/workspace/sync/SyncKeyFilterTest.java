package com.gongbang.workspace.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class SyncKeyFilterTest {

    private static final String VALID_KEY = "test-sync-key-7f3a";
    private static final String WRONG_KEY = "wrong-header-9c1e";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock private FilterChain filterChain;

    private SyncKeyFilter syncKeyFilter;

    @BeforeEach
    void setUp() {
        syncKeyFilter = new SyncKeyFilter(objectMapper);
        ReflectionTestUtils.setField(syncKeyFilter, "syncInternalKey", VALID_KEY);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/internal/documents/1", "/internal/whiteboards/1"})
    @DisplayName("키가 맞으면 체인으로 넘긴다")
    void 키가_맞으면_통과(String uri) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.addHeader("X-Sync-Key", VALID_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();

        syncKeyFilter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("키가 틀리면 401 INVALID_INTERNAL_KEY, 로그에는 이유만 남고 키·헤더 값은 없다")
    void 키가_틀리면_401(CapturedOutput output) throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("PUT", "/internal/documents/1/content");
        request.addHeader("X-Sync-Key", WRONG_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();

        syncKeyFilter.doFilter(request, response, filterChain);

        assertUnauthorized(response);
        verifyNoInteractions(filterChain);
        assertThat(output)
                .contains("PUT")
                .contains("/internal/documents/1/content")
                .contains("불일치")
                .doesNotContain(VALID_KEY)
                .doesNotContain(WRONG_KEY);
    }

    @Test
    @DisplayName("키 헤더가 없으면 401 INVALID_INTERNAL_KEY, 로그 이유는 「헤더 없음」")
    void 헤더가_없으면_401(CapturedOutput output) throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/internal/whiteboards/1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        syncKeyFilter.doFilter(request, response, filterChain);

        assertUnauthorized(response);
        verifyNoInteractions(filterChain);
        assertThat(output).contains("헤더 없음").doesNotContain(VALID_KEY);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", VALID_KEY})
    @DisplayName("설정값이 비어 있으면 어떤 헤더를 보내도 401 (fail-closed), 로그 이유는 「설정값 없음」")
    void 설정값이_비어_있으면_401(String headerValue, CapturedOutput output) throws Exception {
        ReflectionTestUtils.setField(syncKeyFilter, "syncInternalKey", "");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/documents/1");
        request.addHeader("X-Sync-Key", headerValue);
        MockHttpServletResponse response = new MockHttpServletResponse();

        syncKeyFilter.doFilter(request, response, filterChain);

        assertUnauthorized(response);
        verifyNoInteractions(filterChain);
        assertThat(output).contains("설정값 없음").doesNotContain(VALID_KEY);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "/api/teams/1/documents",
                "/internal/contests/batch",
                "/internal/documentsX"
            })
    @DisplayName("대상 경로가 아니면 키 없이도 통과한다")
    void 대상이_아닌_경로는_통과(String uri) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        MockHttpServletResponse response = new MockHttpServletResponse();

        syncKeyFilter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    private void assertUnauthorized(MockHttpServletResponse response) throws Exception {
        assertThat(response.getStatus()).isEqualTo(401);
        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.get("success").asBoolean()).isFalse();
        assertThat(body.get("error").get("code").asText()).isEqualTo("INVALID_INTERNAL_KEY");
    }
}
