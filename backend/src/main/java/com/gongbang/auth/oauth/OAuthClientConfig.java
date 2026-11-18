package com.gongbang.auth.oauth;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

/**
 * AUTH-10 간편 로그인 제공자 등록 (AGENTS 2-2: Spring Security OAuth2 Client, 카카오는 provider 직접 등록).
 *
 * <p>키(환경 변수)가 비어 있는 제공자는 등록하지 않는다. 그래서 키 없이도 서버가 켜지고, 화면은 GET /api/auth/oauth/providers로 켜진 제공자만
 * 확인한다.
 */
@Configuration
public class OAuthClientConfig {

    private static final String DEFAULT_REDIRECT = "{baseUrl}/login/oauth2/code/{registrationId}";

    @Bean
    public EnabledOAuthProviders enabledOAuthProviders(
            @Value("${app.oauth.redirect-uri-template:}") String redirectTemplate,
            @Value("${app.oauth.kakao.client-id:}") String kakaoId,
            @Value("${app.oauth.kakao.client-secret:}") String kakaoSecret,
            @Value("${app.oauth.kakao.scope:}") String kakaoScope,
            @Value("${app.oauth.google.client-id:}") String googleId,
            @Value("${app.oauth.google.client-secret:}") String googleSecret) {
        String redirect = redirectTemplate.isBlank() ? DEFAULT_REDIRECT : redirectTemplate;
        Map<String, ClientRegistration> registrations = new LinkedHashMap<>();
        if (!kakaoId.isBlank()) {
            ClientRegistration.Builder kakao =
                    ClientRegistration.withRegistrationId("kakao")
                            .clientName("Kakao")
                            .clientId(kakaoId)
                            .clientSecret(kakaoSecret)
                            // 카카오는 client_secret을 본문으로 보낸다
                            .clientAuthenticationMethod(
                                    ClientAuthenticationMethod.CLIENT_SECRET_POST)
                            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                            .redirectUri(redirect)
                            .authorizationUri("https://kauth.kakao.com/oauth/authorize")
                            .tokenUri("https://kauth.kakao.com/oauth/token")
                            .userInfoUri("https://kapi.kakao.com/v2/user/me")
                            .userNameAttributeName("id");
            if (!kakaoScope.isBlank()) {
                kakao.scope(List.of(kakaoScope.trim().split("\\s*,\\s*")));
            }
            registrations.put("kakao", kakao.build());
        }
        if (!googleId.isBlank()) {
            registrations.put(
                    "google",
                    CommonOAuth2Provider.GOOGLE
                            .getBuilder("google")
                            .clientId(googleId)
                            .clientSecret(googleSecret)
                            .redirectUri(redirect)
                            .scope("openid", "email")
                            .build());
        }
        return new EnabledOAuthProviders(registrations);
    }

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository(
            EnabledOAuthProviders providers) {
        return providers::get;
    }

    /** 켜진 제공자 목록 (키가 설정된 것만) */
    public record EnabledOAuthProviders(Map<String, ClientRegistration> registrations) {

        public ClientRegistration get(String registrationId) {
            return registrations.get(registrationId);
        }

        public List<String> names() {
            return List.copyOf(registrations.keySet());
        }
    }
}
