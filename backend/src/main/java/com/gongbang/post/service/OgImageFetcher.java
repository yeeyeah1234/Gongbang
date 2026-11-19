package com.gongbang.post.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

/**
 * PST-002 ⑤: 외부 공모전 링크 페이지의 대표 이미지(og:image) 주소를 읽는다. 못 읽으면 비어 있고 화면은 기본 이미지를 쓴다.
 *
 * <p>사용자가 넣은 주소로 서버가 직접 요청하므로 내부망 접근(SSRF)을 막는다.
 *
 * <ul>
 *   <li>http·https만, 사설·루프백·링크 로컬(169.254 클라우드 메타데이터 포함)·멀티캐스트 주소는 요청하지 않는다.
 *   <li>리다이렉트는 자동으로 따라가지 않고, 한 번씩 위 검사를 다시 한 뒤 최대 {@value #MAX_REDIRECTS}번까지 따라간다.
 *   <li>연결 {@value #CONNECT_TIMEOUT_SECONDS}초, 응답 {@value #REQUEST_TIMEOUT_SECONDS}초, 본문은 앞 {@value
 *       #MAX_BODY_BYTES}바이트만 읽는다.
 * </ul>
 *
 * <p>남는 위험: 검사할 때와 실제로 연결할 때 DNS 답이 바뀌는 경우(DNS rebinding)는 막지 못한다. 서버 방화벽에서 이 앱의 내부망 연결을 막으면 함께
 * 막힌다.
 */
@Component
public class OgImageFetcher {

    static final int MAX_REDIRECTS = 3;
    static final int CONNECT_TIMEOUT_SECONDS = 3;
    static final int REQUEST_TIMEOUT_SECONDS = 5;
    static final int MAX_BODY_BYTES = 1_000_000;
    // DB 명세서 recruit_post.ext_contest_image_url VARCHAR(500)
    static final int IMAGE_URL_MAX_LENGTH = 500;

    private static final String USER_AGENT = "Mozilla/5.0 (compatible; GongbangBot/1.0)";

    private final HttpClient client =
            HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                    .build();
    private final Predicate<InetAddress> addressAllowed;

    public OgImageFetcher() {
        this(OgImageFetcher::isPublicAddress);
    }

    /** 테스트에서 로컬 서버를 쓰려고 주소 검사를 바꾼다. */
    OgImageFetcher(Predicate<InetAddress> addressAllowed) {
        this.addressAllowed = addressAllowed;
    }

    public Optional<String> fetch(String pageUrl) {
        try {
            URI uri = URI.create(pageUrl);
            for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
                if (!isAllowed(uri)) {
                    return Optional.empty();
                }
                HttpRequest request =
                        HttpRequest.newBuilder(uri)
                                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                                .header("User-Agent", USER_AGENT)
                                .header("Accept", "text/html")
                                .GET()
                                .build();
                HttpResponse<InputStream> response =
                        client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                try (InputStream body = response.body()) {
                    int status = response.statusCode();
                    if (status >= 300 && status < 400) {
                        Optional<String> location = response.headers().firstValue("Location");
                        if (location.isEmpty()) {
                            return Optional.empty();
                        }
                        uri = uri.resolve(location.get());
                        continue;
                    }
                    String contentType = response.headers().firstValue("Content-Type").orElse("");
                    if (status != 200 || !contentType.toLowerCase(Locale.ROOT).contains("html")) {
                        return Optional.empty();
                    }
                    return extract(body.readNBytes(MAX_BODY_BYTES), uri.toString());
                }
            }
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (IOException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** HTML에서 og:image를 찾아 절대 주소로 바꾼다. http(s)가 아니거나 너무 길면 쓰지 않는다. */
    static Optional<String> extract(byte[] html, String baseUrl) throws IOException {
        // 문자 집합은 meta charset을 보고 정한다
        Document document = Jsoup.parse(new ByteArrayInputStream(html), null, baseUrl);
        Element meta = document.selectFirst("meta[property=og:image], meta[name=og:image]");
        if (meta == null) {
            return Optional.empty();
        }
        String imageUrl = meta.absUrl("content");
        String lower = imageUrl.toLowerCase(Locale.ROOT);
        if (!(lower.startsWith("http://") || lower.startsWith("https://"))
                || imageUrl.length() > IMAGE_URL_MAX_LENGTH) {
            return Optional.empty();
        }
        return Optional.of(imageUrl);
    }

    /** http(s)이고, 호스트의 모든 주소가 허용될 때만 요청한다. */
    boolean isAllowed(URI uri) {
        String scheme = uri.getScheme();
        if (scheme == null
                || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                || uri.getHost() == null) {
            return false;
        }
        try {
            InetAddress[] addresses = InetAddress.getAllByName(uri.getHost());
            for (InetAddress address : addresses) {
                if (!addressAllowed.test(address)) {
                    return false;
                }
            }
            return addresses.length > 0;
        } catch (UnknownHostException e) {
            return false;
        }
    }

    static boolean isPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }
        byte[] b = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = b[0] & 0xff;
            int second = b[1] & 0xff;
            return first != 0 // 0.0.0.0/8
                    && !(first == 100 && second >= 64 && second <= 127) // 100.64.0.0/10 공유 주소
                    && !(first == 192 && second == 0 && (b[2] & 0xff) == 0) // 192.0.0.0/24
                    && !(first == 198 && (second == 18 || second == 19)) // 198.18.0.0/15
                    && first < 240; // 240.0.0.0/4 예약, 255.255.255.255
        }
        if (address instanceof Inet6Address) {
            // fc00::/7 고유 로컬 주소
            return (b[0] & 0xfe) != 0xfc;
        }
        return false;
    }
}
