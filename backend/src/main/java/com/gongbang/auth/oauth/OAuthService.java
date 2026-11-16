package com.gongbang.auth.oauth;

import com.gongbang.auth.domain.VerificationPurpose;
import com.gongbang.auth.dto.LoginResult;
import com.gongbang.auth.dto.OAuthSignupRequest;
import com.gongbang.auth.jwt.JwtProvider;
import com.gongbang.auth.service.AuthService;
import com.gongbang.auth.service.EmailVerificationService;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.domain.LoginType;
import com.gongbang.member.domain.Member;
import com.gongbang.member.service.MemberService;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** AUTH-10 간편 로그인 (카카오·구글): 백엔드가 직접 리다이렉트 → 1회용 키 → 로그인 토큰 */
@Service
@RequiredArgsConstructor
public class OAuthService {

    private static final String SIGNUP_TOKEN = "oauth-signup";
    private static final Duration SIGNUP_TOKEN_TTL = Duration.ofMinutes(30);
    private static final String SEP = "|";

    private final MemberService memberService;
    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;
    private final OAuthCodeStore codeStore;
    private final JwtProvider jwtProvider;

    /**
     * 소셜 로그인 성공 직후. 프론트 /oauth/callback 뒤에 붙일 쿼리(code= 또는 error=)를 돌려준다.
     *
     * @param registrationId kakao / google
     */
    public String handleProviderLogin(String registrationId, Map<String, Object> attributes) {
        LoginType loginType = "google".equals(registrationId) ? LoginType.GOOGLE : LoginType.KAKAO;
        String providerId = extractId(loginType, attributes);
        String email = extractEmail(loginType, attributes);
        if (providerId == null) {
            return "error=FAILED&provider=" + registrationId;
        }
        Optional<Member> existing = memberService.findBySocialId(loginType, providerId);
        if (existing.isPresent()) {
            Member member = existing.get();
            if (member.isWithdrawn()) {
                // MY-11 ⑤: 복원 대기 계정
                return "error=WITHDRAWN&provider=" + registrationId;
            }
            if (!member.isActive()) {
                return "error=FAILED&provider=" + registrationId;
            }
            return "code=" + codeStore.save(new OAuthPending(member.getId(), loginType, providerId, email));
        }
        // ⑦ 같은 이메일이 이미 다른 방식으로 가입돼 있으면 그 방식으로 안내
        if (email != null && memberService.findByEmail(email).isPresent()) {
            return "error=EMAIL_REGISTERED&provider=" + registrationId;
        }
        // ⑤ 처음이면 추가 정보 입력으로
        return "code=" + codeStore.save(new OAuthPending(null, loginType, providerId, email));
    }

    /** POST /api/auth/oauth/token */
    public OAuthExchange exchange(String code) {
        OAuthPending pending =
                codeStore
                        .consume(code)
                        .orElseThrow(() -> new BusinessException(ErrorCode.OAUTH_CODE_INVALID));
        if (pending.isExistingMember()) {
            return OAuthExchange.login(authService.loginSocial(pending.memberId()));
        }
        String subject =
                String.join(
                        SEP,
                        pending.loginType().name(),
                        pending.providerId(),
                        pending.email() == null ? "" : pending.email());
        return OAuthExchange.signup(
                jwtProvider.createPurposeToken(SIGNUP_TOKEN, subject, SIGNUP_TOKEN_TTL),
                pending.loginType(),
                pending.email());
    }

    /** POST /api/auth/oauth/signup */
    public LoginResult signup(OAuthSignupRequest request) {
        String[] parts =
                jwtProvider
                        .parsePurposeToken(request.signupToken(), SIGNUP_TOKEN)
                        .orElseThrow(() -> new BusinessException(ErrorCode.TOKEN_EXPIRED))
                        .split("\\" + SEP, -1);
        LoginType loginType = LoginType.valueOf(parts[0]);
        String providerId = parts[1];
        String email = parts.length > 2 && !parts[2].isBlank() ? parts[2] : null;
        if (email == null) {
            // ⑥ 제공자에게서 이메일을 받지 못했으면 직접 입력하고 인증번호로 확인한다
            if (request.email() == null || request.verificationToken() == null) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "이메일 인증을 완료해 주세요");
            }
            email =
                    emailVerificationService.requireVerifiedEmail(
                            request.verificationToken(), VerificationPurpose.SIGNUP, request.email());
        }
        Member member =
                memberService.createSocialMember(
                        loginType,
                        providerId,
                        email,
                        request.nickname(),
                        request.terms(),
                        request.interestCategoryCodes());
        return authService.completeSocialSignup(member.getId());
    }

    private static String extractId(LoginType loginType, Map<String, Object> attributes) {
        Object id = attributes.get(loginType == LoginType.GOOGLE ? "sub" : "id");
        return id == null ? null : String.valueOf(id);
    }

    /** 확인된 이메일만 쓴다. 없으면 null → 가입 때 직접 입력·인증 */
    private static String extractEmail(LoginType loginType, Map<String, Object> attributes) {
        Object email;
        Object verified;
        if (loginType == LoginType.GOOGLE) {
            email = attributes.get("email");
            verified = attributes.get("email_verified");
        } else {
            Object account = attributes.get("kakao_account");
            if (!(account instanceof Map<?, ?> map)) {
                return null;
            }
            email = map.get("email");
            verified = map.get("is_email_verified");
        }
        if (email == null || !Boolean.parseBoolean(String.valueOf(verified))) {
            return null;
        }
        return EmailVerificationService.normalize(String.valueOf(email));
    }

    /** exchange 결과: 기존 회원이면 loginResult, 처음이면 signupToken */
    public record OAuthExchange(
            LoginResult loginResult, String signupToken, LoginType loginType, String email) {

        static OAuthExchange login(LoginResult loginResult) {
            return new OAuthExchange(loginResult, null, null, null);
        }

        static OAuthExchange signup(String signupToken, LoginType loginType, String email) {
            return new OAuthExchange(null, signupToken, loginType, email);
        }

        public boolean isNewMember() {
            return loginResult == null;
        }
    }
}
