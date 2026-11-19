package com.gongbang.contest.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gongbang.global.common.ApiResponse;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * CRW-01, AGENTS.md 9장: /internal/contests/** 는 크롤러 키(CRAWLER_INTERNAL_KEY)가 맞을 때만 받는다. 동기화 서버
 * 키(SYNC_INTERNAL_KEY) 등 다른 내부 키로는 열리지 않는다. 키는 X-Crawler-Key 헤더로 받는다 (API 명세서 CRW-01 비고).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrawlerKeyFilter extends OncePerRequestFilter {

    private static final String INTERNAL_PATH_PREFIX = "/internal/contests";
    private static final String HEADER_NAME = "X-Crawler-Key";

    private final ObjectMapper objectMapper;

    @Value("${app.crawler.internal-key:}")
    private String crawlerInternalKey;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(INTERNAL_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String rejectReason = rejectReason(request.getHeader(HEADER_NAME));
        if (rejectReason != null) {
            // 키 값·헤더 값은 남기지 않는다
            log.warn(
                    "크롤러 내부 키 확인 실패로 401. method={}, path={}, reason={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    rejectReason);
            writeUnauthorized(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    /** 통과면 null, 거절이면 이유 */
    private String rejectReason(String key) {
        if (crawlerInternalKey == null || crawlerInternalKey.isBlank()) {
            return "설정값 없음";
        }
        if (key == null) {
            return "헤더 없음";
        }
        // 비교 시간이 내용에 따라 달라지지 않게 constant-time 비교
        boolean matches =
                MessageDigest.isEqual(
                        crawlerInternalKey.getBytes(StandardCharsets.UTF_8),
                        key.getBytes(StandardCharsets.UTF_8));
        return matches ? null : "불일치";
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(ErrorCode.INVALID_INTERNAL_KEY.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter()
                .write(
                        objectMapper.writeValueAsString(
                                ApiResponse.failure(
                                        ErrorResponse.from(ErrorCode.INVALID_INTERNAL_KEY))));
    }
}
