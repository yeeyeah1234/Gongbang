package com.gongbang.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gongbang.auth.jwt.JwtAuthenticationFilter;
import com.gongbang.auth.oauth.OAuthLoginHandlers;
import com.gongbang.global.common.ApiResponse;
import com.gongbang.global.error.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, OAuthLoginHandlers oAuthLoginHandlers)
            throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // AUTH-12: 토큰이 있으면 로그인 회원을 넣는다 (공개 API에서도)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // AUTH-10: GET /oauth2/authorization/{kakao|google} → 끝나면 프론트 /oauth/callback 으로
                .oauth2Login(oauth -> oauth
                        .loginPage("/login")
                        .successHandler(oAuthLoginHandlers)
                        .failureHandler(oAuthLoginHandlers))
                .exceptionHandling(ex -> ex
                        // 로그인이 필요한데 토큰이 없거나 만료 → 401 UNAUTHORIZED (컨트롤러 앞에서)
                        .authenticationEntryPoint((request, response, e) ->
                                writeError(response, ErrorCode.UNAUTHORIZED))
                        // 운영자 API를 일반 회원이 부름 → 403 NOT_ADMIN (AGENTS 9)
                        .accessDeniedHandler((request, response, e) ->
                                writeError(response, ErrorCode.NOT_ADMIN)))
                .authorizeHttpRequests(auth -> auth
                        // 서버끼리 부르는 API는 각자 내부 키 필터가 확인한다 (일반 토큰으로 열리지 않음)
                        .requestMatchers("/internal/**").permitAll()
                        // AUTH-12 ①: 비회원은 메인, 공모전 목록·상세, 게시글 보기만
                        .requestMatchers(HttpMethod.GET,
                                "/api/contests", "/api/contests/*", "/api/categories",
                                "/api/search/**", "/api/posts", "/api/posts/**",
                                "/api/members/*/profile", "/api/members/check-nickname",
                                "/api/auth/find-email", "/api/auth/oauth/providers",
                                "/uploads/**").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/auth/email/send-code", "/api/auth/email/verify",
                                "/api/auth/signup", "/api/auth/login", "/api/auth/token/refresh",
                                "/api/auth/password/reset", "/api/auth/restore",
                                "/api/auth/oauth/token", "/api/auth/oauth/signup").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // 그 밖의 /api/**는 로그인 필요 (글쓰기·댓글·북마크·채팅·워크스페이스·마이페이지·알림)
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll() // TODO: JWT 인증 구현 후 접근 권한 세분화
                );

        return http.build();
    }

    private void writeError(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(
                response.getWriter(),
                ApiResponse.failure(errorCode.getCode(), errorCode.getMessage()));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
