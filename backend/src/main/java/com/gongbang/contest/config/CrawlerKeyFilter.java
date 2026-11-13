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
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * CRW-01, AGENTS.md 9장: /internal/contests/** 는 크롤러 키(CRAWLER_INTERNAL_KEY)가 맞을 때만 받는다.
 * 동기화 서버 키(SYNC_INTERNAL_KEY) 등 다른 내부 키로는 열리지 않는다. 키는 X-Crawler-Key 헤더로 받는다
 * (API 명세서 CRW-01 비고).
 */
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
        String key = request.getHeader(HEADER_NAME);
        if (crawlerInternalKey.isBlank() || !crawlerInternalKey.equals(key)) {
            writeUnauthorized(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(ErrorCode.INVALID_INTERNAL_KEY.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter()
                .write(
                        objectMapper.writeValueAsString(
                                ApiResponse.failure(
                                        ErrorResponse.from(ErrorCode.INVALID_INTERNAL_KEY))));
    }
}
