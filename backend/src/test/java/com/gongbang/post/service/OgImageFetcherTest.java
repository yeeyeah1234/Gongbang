package com.gongbang.post.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OgImageFetcherTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private static Optional<String> extract(String html, String baseUrl) throws IOException {
        return OgImageFetcher.extract(html.getBytes(StandardCharsets.UTF_8), baseUrl);
    }

    @Test
    @DisplayName("og:image가 있으면 그 주소를 쓴다")
    void og_image를_읽는다() throws IOException {
        String html =
                "<html><head><meta property=\"og:image\" content=\"https://cdn.example.com/a.png\">"
                        + "</head></html>";

        assertThat(extract(html, "https://example.com/contest"))
                .contains("https://cdn.example.com/a.png");
    }

    @Test
    @DisplayName("상대 주소는 페이지 주소 기준 절대 주소로 바꾼다")
    void 상대_주소를_절대_주소로_바꾼다() throws IOException {
        String html = "<meta name=\"og:image\" content=\"/img/poster.jpg\">";

        assertThat(extract(html, "https://example.com/contest/1"))
                .contains("https://example.com/img/poster.jpg");
    }

    @Test
    @DisplayName("og:image가 없거나 http(s)가 아니거나 500자를 넘으면 쓰지 않는다")
    void 쓸_수_없는_이미지는_비운다() throws IOException {
        String base = "https://example.com/";
        String tooLong = "https://example.com/" + "a".repeat(OgImageFetcher.IMAGE_URL_MAX_LENGTH);

        assertThat(extract("<title>공모전</title>", base)).isEmpty();
        assertThat(extract("<meta property=\"og:image\" content=\"javascript:alert(1)\">", base))
                .isEmpty();
        assertThat(extract("<meta property=\"og:image\" content=\"" + tooLong + "\">", base))
                .isEmpty();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "http://127.0.0.1/",
                "http://localhost/",
                "http://10.0.0.1/",
                "http://172.16.0.1/",
                "http://192.168.0.1/",
                "http://169.254.169.254/latest/meta-data/",
                "http://100.64.0.1/",
                "http://0.0.0.0/",
                "http://[::1]/",
                "http://[fd00::1]/",
                "http://[::ffff:127.0.0.1]/",
                "ftp://93.184.216.34/",
                "file:///etc/passwd"
            })
    @DisplayName("내부망 주소와 http(s)가 아닌 주소는 요청하지 않는다")
    void 내부망_주소는_막는다(String url) {
        assertThat(new OgImageFetcher().isAllowed(URI.create(url))).isFalse();
    }

    @Test
    @DisplayName("공인 IP의 http(s) 주소는 요청한다")
    void 공인_주소는_허용한다() {
        OgImageFetcher fetcher = new OgImageFetcher();

        assertThat(fetcher.isAllowed(URI.create("https://93.184.216.34/contest"))).isTrue();
        assertThat(fetcher.isAllowed(URI.create("http://[2606:2800:220:1::]/"))).isTrue();
    }

    @Test
    @DisplayName("기본 설정으로는 로컬 서버도 요청하지 않는다")
    void 기본_설정은_로컬_서버를_요청하지_않는다() throws IOException {
        AtomicInteger hits =
                startServer("text/html", "<meta property=\"og:image\" content=\"/a.png\">");

        assertThat(new OgImageFetcher().fetch(localUrl("/page"))).isEmpty();
        assertThat(hits.get()).isZero();
    }

    @Test
    @DisplayName("페이지를 받아 og:image를 읽는다")
    void 페이지를_받아_og_image를_읽는다() throws IOException {
        startServer("text/html; charset=UTF-8", "<meta property=\"og:image\" content=\"/a.png\">");

        assertThat(loopbackOnly().fetch(localUrl("/page"))).contains(localUrl("/a.png"));
    }

    @Test
    @DisplayName("HTML이 아니면 읽지 않는다")
    void HTML이_아니면_읽지_않는다() throws IOException {
        startServer("application/json", "<meta property=\"og:image\" content=\"/a.png\">");

        assertThat(loopbackOnly().fetch(localUrl("/page"))).isEmpty();
    }

    @Test
    @DisplayName("리다이렉트한 곳이 막힌 주소면 따라가지 않는다")
    void 막힌_주소로의_리다이렉트는_따라가지_않는다() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext(
                "/page",
                exchange -> {
                    exchange.getResponseHeaders().add("Location", "http://10.0.0.1/secret");
                    exchange.sendResponseHeaders(302, -1);
                    exchange.close();
                });
        server.start();

        assertThat(loopbackOnly().fetch(localUrl("/page"))).isEmpty();
    }

    @Test
    @DisplayName("리다이렉트가 너무 많으면 멈춘다")
    void 리다이렉트가_너무_많으면_멈춘다() throws IOException {
        AtomicInteger hits = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext(
                "/loop",
                exchange -> {
                    hits.incrementAndGet();
                    exchange.getResponseHeaders().add("Location", "/loop");
                    exchange.sendResponseHeaders(302, -1);
                    exchange.close();
                });
        server.start();

        assertThat(loopbackOnly().fetch(localUrl("/loop"))).isEmpty();
        assertThat(hits.get()).isEqualTo(OgImageFetcher.MAX_REDIRECTS + 1);
    }

    private static OgImageFetcher loopbackOnly() {
        return new OgImageFetcher(InetAddress::isLoopbackAddress);
    }

    private String localUrl(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    private AtomicInteger startServer(String contentType, String body) throws IOException {
        AtomicInteger hits = new AtomicInteger();
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext(
                "/page",
                exchange -> {
                    hits.incrementAndGet();
                    exchange.getResponseHeaders().add("Content-Type", contentType);
                    exchange.sendResponseHeaders(200, bytes.length);
                    try (OutputStream out = exchange.getResponseBody()) {
                        out.write(bytes);
                    }
                });
        server.start();
        return hits;
    }
}
