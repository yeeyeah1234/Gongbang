package com.gongbang.workspace.sync;

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
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * DOC-08·DOC-10·WB-10, AGENTS.md 9장: /internal/documents/**, /internal/whiteboards/** 는 동기화 서버
 * 키(SYNC_INTERNAL_KEY)가 맞을 때만 받는다. 키는 X-Sync-Key 헤더로 받고, 설정값이 비어 있으면 모두 거절한다(fail-closed). 크롤러 키 등
 * 다른 내부 키로는 열리지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SyncKeyFilter extends OncePerRequestFilter {

    private static final List<String> INTERNAL_PATH_PREFIXES =
            List.of("/internal/documents", "/internal/whiteboards");
    private static final String HEADER_NAME = "X-Sync-Key";

    private final ObjectMapper objectMapper;

    @Value("${app.sync.internal-key:}")
    private String syncInternalKey;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return INTERNAL_PATH_PREFIXES.stream()
                .noneMatch(prefix -> uri.equals(prefix) || uri.startsWith(prefix + "/"));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String rejectReason = rejectReason(request.getHeader(HEADER_NAME));
        if (rejectReason != null) {
            // 키 값·헤더 값은 남기지 않는다
            log.warn(
                    "동기화 서버 내부 키 확인 실패로 401. method={}, path={}, reason={}",
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
        if (syncInternalKey == null || syncInternalKey.isBlank()) {
            return "설정값 없음";
        }
        if (key == null) {
            return "헤더 없음";
        }
        // 비교 시간이 내용에 따라 달라지지 않게 constant-time 비교
        boolean matches =
                MessageDigest.isEqual(
                        syncInternalKey.getBytes(StandardCharsets.UTF_8),
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
