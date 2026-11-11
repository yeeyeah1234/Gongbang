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
 * CRW-01, AGENTS.md 9장: /internal/contests/** 는 크롤러 전용 키가 맞을 때만 받는다. TODO(확인 필요):
 * 헤더명(X-Crawler-Key)이 API 명세서에 아직 정해지지 않아 임시로 정함.
 */
@Component
@RequiredArgsConstructor
public class CrawlerKeyFilter extends OncePerRequestFilter {

    private static final String INTERNAL_PATH_PREFIX = "/internal/contests";
    private static final String HEADER_NAME = "X-Crawler-Key";

    private final ObjectMapper objectMapper;

    @Value("${app.crawler.api-key:}")
    private String crawlerApiKey;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(INTERNAL_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String key = request.getHeader(HEADER_NAME);
        if (crawlerApiKey.isBlank() || !crawlerApiKey.equals(key)) {
            writeUnauthorized(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(ErrorCode.INVALID_CRAWLER_KEY.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter()
                .write(
                        objectMapper.writeValueAsString(
                                ApiResponse.failure(
                                        ErrorResponse.from(ErrorCode.INVALID_CRAWLER_KEY))));
    }
}
