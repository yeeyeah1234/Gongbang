package com.gongbang.auth.support;

import com.gongbang.member.domain.MemberRole;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.test.context.support.WithSecurityContext;

/**
 * 테스트에서 가짜 로그인 회원을 넣는다. 프로덕션 코드에 고정 ID를 넣지 않기 위한 것.
 *
 * <pre>
 * &#64;Test
 * &#64;WithLoginMember(memberId = 1)
 * void 팀원이_아니면_403() { ... }  // 컨트롤러의 &#64;AuthenticationPrincipal LoginMember 로 들어간다
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@WithSecurityContext(factory = WithLoginMemberSecurityContextFactory.class)
public @interface WithLoginMember {

    long memberId() default 1L;

    MemberRole role() default MemberRole.USER;
}
