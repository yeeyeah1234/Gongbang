package com.gongbang.auth.support;

import com.gongbang.auth.domain.LoginMember;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

public class WithLoginMemberSecurityContextFactory
        implements WithSecurityContextFactory<WithLoginMember> {

    @Override
    public SecurityContext createSecurityContext(WithLoginMember annotation) {
        LoginMember loginMember = new LoginMember(annotation.memberId(), annotation.role());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        loginMember,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + annotation.role().name()))));
        return context;
    }
}
