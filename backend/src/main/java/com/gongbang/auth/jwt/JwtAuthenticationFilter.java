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
            jwtProvider
                    .parseAccessToken(header.substring(BEARER.length()))
                    .ifPresent(
                            claims -> {
                                var current =
                                        members.findActiveMember(claims.memberId()).orElse(null);
                                if (current == null || current.isSuspended()) return;
                                LoginMember loginMember =
                                        new LoginMember(claims.memberId(), current.getRole());
                                var authentication =
                                        new UsernamePasswordAuthenticationToken(
                                                loginMember,
                                                null,
                                                List.of(
                                                        new SimpleGrantedAuthority(
                                                                "ROLE_"
                                                                        + current.getRole()
                                                                                .name())));
                                SecurityContextHolder.getContext()
                                        .setAuthentication(authentication);
                            });
        }
        chain.doFilter(request, response);
    }
}
