package com.gongbang.auth.jwt;

import com.gongbang.auth.domain.LoginMember;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authorization: Bearer {accessToken} 헤더를 읽어 로그인 회원을 넣는다. 공개 API에서도 토큰이 있으면 넣어 준다(북마크 표시 등). 토큰이
 * 없거나 틀리면 그냥 비로그인으로 두고, 막을지는 SecurityConfig가 정한다.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final JwtProvider jwtProvider;
    private final com.gongbang.member.service.MemberService members;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER)) {
            var claims =
                    jwtProvider.parseAccessToken(header.substring(BEARER.length())).orElse(null);
            if (claims != null) {
                var current = members.findActiveMember(claims.memberId()).orElse(null);
                if (current != null && current.isSuspended()) {
                    reject(response, "ACCOUNT_SUSPENDED", "이용 정지된 계정입니다");
                    return;
                }
                if (current != null
                        && current.isNicknameChangeRequired()
                        && !nicknameAllowed(request)) {
                    reject(response, "NICKNAME_CHANGE_REQUIRED", "닉네임을 변경한 후 이용해 주세요");
                    return;
                }
                if (current != null) {
                    var authentication =
                            new UsernamePasswordAuthenticationToken(
                                    new LoginMember(claims.memberId(), current.getRole()),
                                    null,
                                    List.of(
                                            new SimpleGrantedAuthority(
                                                    "ROLE_" + current.getRole().name())));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        }
        chain.doFilter(request, response);
    }

    private boolean nicknameAllowed(HttpServletRequest request) {
        String path = request.getServletPath();
        String method = request.getMethod();
        return ("/api/members/me".equals(path) && ("GET".equals(method) || "PATCH".equals(method)))
                || ("/api/auth/logout".equals(path) && "POST".equals(method));
    }

    private void reject(HttpServletResponse response, String code, String message)
            throws IOException {
        response.setStatus(403);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter()
                .write(
                        "{\"success\":false,\"data\":null,\"error\":{\"code\":\""
                                + code
                                + "\",\"message\":\""
                                + message
                                + "\"}}");
    }
}
