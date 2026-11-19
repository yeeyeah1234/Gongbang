package com.gongbang.workspace.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.ConnectException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@ExtendWith(OutputCaptureExtension.class)
class SyncServerClientTest {

    private static final String SERVER_URL = "http://sync.test";
    private static final String KEY = "test-sync-key-4b8d";

    private RestClient restClient;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        restClient = builder.build();
    }

    @Test
    @DisplayName("주소가 비어 있으면 요청을 보내지 않고 WARN 로그만 남긴다")
    void 주소가_비어_있으면_요청_안_감(CapturedOutput output) {
        SyncServerClient client = new SyncServerClient(restClient, "", KEY);

        client.disconnect(1L, 2L);

        server.verify();
        assertThat(output).contains("WARN").contains("teamId=1").doesNotContain(KEY);
    }

    @Test
    @DisplayName("키가 비어 있으면 요청을 보내지 않는다")
    void 키가_비어_있으면_요청_안_감() {
        SyncServerClient client = new SyncServerClient(restClient, SERVER_URL, "");

        client.disconnect(1L, 2L);

        server.verify();
    }

    @Test
    @DisplayName("정상 호출이면 주소·헤더·body를 그대로 보낸다 (주소 끝의 / 는 하나로 합친다)")
    void 정상_호출() {
        server.expect(requestTo(SERVER_URL + "/internal/disconnect"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Sync-Key", KEY))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"teamId\":1,\"memberId\":2}", true))
                .andRespond(withSuccess());
        SyncServerClient client = new SyncServerClient(restClient, SERVER_URL + "/", KEY);

        client.disconnect(1L, 2L);

        server.verify();
    }

    @Test
    @DisplayName("서버가 500이면 예외 없이 WARN 로그를 남기고, 로그에 키는 없다")
    void 서버_500이면_예외_없음(CapturedOutput output) {
        server.expect(requestTo(SERVER_URL + "/internal/disconnect")).andRespond(withServerError());
        SyncServerClient client = new SyncServerClient(restClient, SERVER_URL, KEY);

        assertThatNoException().isThrownBy(() -> client.disconnect(1L, 2L));

        server.verify();
        assertThat(output)
                .contains("WARN")
                .contains("teamId=1")
                .contains("memberId=2")
                .contains("HTTP 500")
                .doesNotContain(KEY);
    }

    @Test
    @DisplayName("연결에 실패하면 예외 없이 WARN 로그를 남긴다")
    void 연결_실패면_예외_없음(CapturedOutput output) {
        server.expect(requestTo(SERVER_URL + "/internal/disconnect"))
                .andRespond(withException(new ConnectException("Connection refused")));
        SyncServerClient client = new SyncServerClient(restClient, SERVER_URL, KEY);

        assertThatNoException().isThrownBy(() -> client.disconnect(1L, 2L));

        assertThat(output).contains("WARN").contains("ResourceAccessException").doesNotContain(KEY);
    }

    @Test
    @DisplayName("주소에 http:// 가 빠진 잘못된 주소면 예외 없이 WARN 로그를 남긴다")
    void 잘못된_주소면_예외_없음(CapturedOutput output) {
        // 실제 요청 팩토리를 쓰는 생성자로 만들어, 잘못된 주소가 실제로 어떻게 실패하는지 확인한다
        SyncServerClient client =
                new SyncServerClient(
                        RestClient.builder(),
                        "127.0.0.1:1234",
                        KEY,
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(2));

        assertThatNoException().isThrownBy(() -> client.disconnect(1L, 2L));

        assertThat(output)
                .contains("WARN")
                .contains("teamId=1")
                .contains("memberId=2")
                .doesNotContain(KEY);
    }
}
