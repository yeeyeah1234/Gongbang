package com.gongbang.workspace.sync;

import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * WS-06: Spring이 문서·보드 동기화 서버(Node, SYNC_SERVER_URL)를 부르는 클라이언트. 키는 X-Sync-Key 헤더로
 * 보낸다(SYNC_INTERNAL_KEY). 실패해도 예외를 던지지 않고 WARN 로그만 남긴다. 키 값은 로그에 남기지 않는다.
 */
@Slf4j
@Component
public class SyncServerClient {

    private static final String DISCONNECT_PATH = "/internal/disconnect";
    private static final String HEADER_NAME = "X-Sync-Key";

    private final RestClient restClient;
    private final String serverUrl;
    private final String internalKey;

    @Autowired
    public SyncServerClient(
            RestClient.Builder restClientBuilder,
            @Value("${app.sync.server-url:}") String serverUrl,
            @Value("${app.sync.internal-key:}") String internalKey,
            @Value("${app.sync.connect-timeout:1s}") Duration connectTimeout,
            @Value("${app.sync.read-timeout:2s}") Duration readTimeout) {
        this(
                restClientBuilder
                        .requestFactory(requestFactory(connectTimeout, readTimeout))
                        .build(),
                serverUrl,
                internalKey);
    }

    /** 테스트에서 MockRestServiceServer에 묶은 RestClient를 넣을 때 쓴다 */
    SyncServerClient(RestClient restClient, String serverUrl, String internalKey) {
        this.restClient = restClient;
        this.serverUrl = serverUrl;
        this.internalKey = internalKey;
    }

    /**
     * 팀에서 나간(또는 강제 퇴장된) 회원의 문서·보드 연결을 끊으라고 동기화 서버에 알린다.
     *
     * <p><b>반드시 DB 트랜잭션이 커밋된 뒤에 부른다</b> (예: {@code @TransactionalEventListener(phase =
     * AFTER_COMMIT)}). 커밋 전에 부르면 롤백돼도 연결은 끊기고, 끊긴 브라우저가 바로 다시 연결할 때 아직 팀원으로 보일 수 있다.
     *
     * <p>주소·키가 비어 있거나 요청이 실패(연결 실패, 시간 초과, 4xx/5xx, 잘못된 주소)해도 예외를 던지지 않는다. 부르는 쪽의 결과에 영향을 주지 않는다.
     *
     * <p>동기화 서버가 응답하지 않으면 최대 약 3초(연결 1초 + 응답 2초) 기다린다. 요청 스레드에서 부르면 응답이 그만큼 늦어질 수 있다.
     */
    public void disconnect(Long teamId, Long memberId) {
        if (isBlank(serverUrl) || isBlank(internalKey)) {
            log.warn(
                    "동기화 서버 주소 또는 키가 설정되지 않아 연결 끊기 요청을 보내지 않습니다. teamId={}, memberId={}",
                    teamId,
                    memberId);
            return;
        }
        try {
            restClient
                    .post()
                    .uri(stripTrailingSlash(serverUrl) + DISCONNECT_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HEADER_NAME, internalKey)
                    .body(new SyncDisconnectRequest(teamId, memberId))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException e) {
            // RestClientException(연결 실패·시간 초과·4xx/5xx)뿐 아니라 잘못된 주소(IllegalArgumentException 등)도 밖으로
            // 던지지 않는다
            log.warn(
                    "동기화 서버 연결 끊기 요청 실패. teamId={}, memberId={}, 원인={}",
                    teamId,
                    memberId,
                    summarize(e));
        }
    }

    private static SimpleClientHttpRequestFactory requestFactory(
            Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return factory;
    }

    /** 응답 body·요청 헤더·예외 메시지는 넣지 않는다 (키가 로그에 나가지 않게) */
    private static String summarize(RuntimeException e) {
        if (e instanceof RestClientResponseException responseException) {
            return "HTTP " + responseException.getStatusCode().value();
        }
        Throwable cause = e.getCause();
        return cause == null
                ? e.getClass().getSimpleName()
                : e.getClass().getSimpleName() + " (" + cause.getClass().getSimpleName() + ")";
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
