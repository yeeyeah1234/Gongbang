package com.gongbang.auth.service;

import com.gongbang.auth.domain.RefreshToken;
import com.gongbang.auth.domain.VerificationPurpose;
import com.gongbang.auth.dto.FindEmailResponse;
import com.gongbang.auth.dto.LoginRequest;
import com.gongbang.auth.dto.LoginResult;
import com.gongbang.auth.dto.SignupRequest;
import com.gongbang.auth.jwt.JwtProvider;
import com.gongbang.auth.repository.RefreshTokenRepository;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.domain.Member;
import com.gongbang.member.service.MemberService;
import com.gongbang.notification.domain.NotificationType;
import com.gongbang.notification.service.EmailOutboxService;
import com.gongbang.notification.service.NotificationService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** AUTH-01·02·07·11, MY-11 복원: 로그인 토큰을 내고 거두는 곳 */
@Service
@RequiredArgsConstructor
public class AuthService {

    /** AUTH-01 ⑨ 같은 IP 1시간 30번 */
    private static final int LOGIN_LIMIT_PER_IP = 30;

    /** AUTH-02 ① 1시간 10번 */
    private static final int FIND_EMAIL_LIMIT_PER_IP = 10;

    private static final String EXTRA_VERIFY_TOKEN = "verified:LOGIN";
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    private final MemberService memberService;
    private final EmailVerificationService emailVerificationService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProvider jwtProvider;
    private final PasswordEncoder passwordEncoder;
    private final RateLimiter rateLimiter;
    private final EmailOutboxService emailOutboxService;
    private final NotificationService notificationService;

    @Value("${app.jwt.refresh-days:1}")
    private long refreshDays;

    @Value("${app.jwt.remember-me-days:30}")
    private long rememberMeDays;

    /** AUTH-04·07 가입 → 바로 로그인 */
    @Transactional
    public LoginResult signup(SignupRequest request) {
        String email =
                emailVerificationService.requireVerifiedEmail(
                        request.verificationToken(), VerificationPurpose.SIGNUP, request.email());
        PasswordPolicy.validate(request.password());
        Member member =
                memberService.createEmailMember(
                        email, passwordEncoder.encode(request.password()), request);
        emailOutboxService.enqueueSignupComplete(member);
        // AUTH-07 ⑤ 환영 알림 1개. 가입 단계에선 이력이 아직 없으므로 안내 문구를 붙인다
        notificationService.send(
                member.getId(),
                NotificationType.WELCOME,
                null,
                null,
                null,
                member.getNickname() + "님, 환영해요! 프로필을 채우면 팀 초대를 더 많이 받아요",
                "/mypage/profile");
        return issue(member, false);
    }

    /** AUTH-01 */
    @Transactional(noRollbackFor = BusinessException.class)
    public LoginResult login(LoginRequest request, String clientIp) {
        rateLimiter.hit(
                "login:ip:" + clientIp,
                LOGIN_LIMIT_PER_IP,
                Duration.ofHours(1),
                "요청이 너무 많아요. 1시간 뒤에 다시 시도해 주세요");
        LocalDateTime now = LocalDateTime.now();
        String email = EmailVerificationService.normalize(request.email());
        // ② 가입되지 않은 이메일은 횟수를 세지 않는다
        Member member =
                memberService
                        .findByEmail(email)
                        .orElseThrow(() -> new BusinessException(ErrorCode.EMAIL_NOT_FOUND));
        checkWithdrawn(member);
        if (!member.isActive() || !member.isEmailMember()) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_FOUND);
        }
        member.clearExpiredLock(now);
        if (member.isLocked(now)) {
            throw new BusinessException(
                    ErrorCode.ACCOUNT_LOCKED,
                    "로그인 시도가 반복돼 30분 동안 로그인할 수 없어요 ("
                            + member.getLockedUntil().format(HM)
                            + " 이후 다시 시도해 주세요)");
        }
        if (member.needsExtraVerification()) {
            // ③ 추가 인증번호를 맞혀야 다시 로그인할 수 있다
            emailVerificationService.requireVerifiedEmail(
                    request.extraVerificationToken() == null
                            ? ""
                            : request.extraVerificationToken(),
                    VerificationPurpose.LOGIN,
                    email);
            member.passExtraVerification();
        }
        if (!passwordEncoder.matches(request.password(), member.getPasswordHash())) {
            int failCount = member.increaseLoginFail(now);
            if (member.isLocked(now)) {
                // ④ 보안 메일
                emailOutboxService.enqueueLoginLocked(member);
                throw new BusinessException(
                        ErrorCode.ACCOUNT_LOCKED,
                        "로그인 시도가 반복돼 30분 동안 로그인할 수 없어요 ("
                                + member.getLockedUntil().format(HM)
                                + " 이후 다시 시도해 주세요)");
            }
            if (member.needsExtraVerification()) {
                throw new BusinessException(ErrorCode.EXTRA_VERIFICATION_REQUIRED);
            }
            int shown =
                    failCount > Member.EXTRA_VERIFICATION_FAIL_COUNT
                            ? failCount - Member.EXTRA_VERIFICATION_FAIL_COUNT
                            : failCount;
            throw new BusinessException(
                    ErrorCode.LOGIN_FAILED, "이메일 또는 비밀번호가 맞지 않아요 (5번 중 " + shown + "번)");
        }
        member.loggedIn(now);
        return issue(member, request.rememberMe());
    }

    /** AUTH-01: refresh 쿠키로 access 재발급. refresh 토큰도 새로 바꾼다(재사용 방지). */
    @Transactional
    public LoginResult refresh(String rawRefreshToken) {
        LocalDateTime now = LocalDateTime.now();
        RefreshToken token = findUsable(rawRefreshToken, now);
        Member member = memberService.getActiveMember(token.getMemberId());
        token.revoke(now);
        return issue(member, token.isRememberMe());
    }

    /** AUTH-11 */
    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository
                .findByTokenHash(TokenHash.sha256(rawRefreshToken))
                .ifPresent(token -> token.revoke(LocalDateTime.now()));
    }

    /** AUTH-02 ① */
    @Transactional(readOnly = true)
    public FindEmailResponse findEmail(String nickname, String clientIp) {
        rateLimiter.hit(
                "find-email:ip:" + clientIp,
                FIND_EMAIL_LIMIT_PER_IP,
                Duration.ofHours(1),
                "요청이 너무 많아요. 1시간 뒤에 다시 시도해 주세요");
        Member member =
                memberService
                        .findByNickname(nickname == null ? "" : nickname.trim())
                        .filter(m -> m.getEmail() != null)
                        .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        return new FindEmailResponse(
                maskEmail(member.getEmail()), member.getLoginType().name(), member.getCreatedAt());
    }

    /** AUTH-02 ②~⑤ */
    @Transactional
    public void resetPassword(String rawEmail, String verificationToken, String newPassword) {
        String email =
                emailVerificationService.requireVerifiedEmail(
                        verificationToken, VerificationPurpose.RESET, rawEmail);
        Member member =
                memberService
                        .findByEmail(email)
                        .orElseThrow(() -> new BusinessException(ErrorCode.EMAIL_NOT_FOUND));
        PasswordPolicy.validate(newPassword);
        if (passwordEncoder.matches(newPassword, member.getPasswordHash())) {
            throw new BusinessException(ErrorCode.SAME_AS_OLD_PASSWORD);
        }
        member.changePassword(passwordEncoder.encode(newPassword));
        // ⑤ 모든 기기 로그아웃 + 보안 메일
        refreshTokenRepository.revokeAll(member.getId(), LocalDateTime.now());
        emailOutboxService.enqueuePasswordChanged(member);
    }

    /** MY-11 ⑥ 계정 복원 */
    @Transactional
    public LoginResult restore(String rawEmail, String verificationToken) {
        String email =
                emailVerificationService.requireVerifiedEmail(
                        verificationToken, VerificationPurpose.RESTORE, rawEmail);
        Member member = memberService.restore(email);
        emailOutboxService.enqueueRestored(member);
        return issue(member, false);
    }

    /** MY-04 ⑤: 지금 기기를 뺀 나머지 refresh 폐기 */
    @Transactional
    public void revokeOtherDevices(Long memberId, String currentRawRefreshToken) {
        LocalDateTime now = LocalDateTime.now();
        Long keepId =
                currentRawRefreshToken == null
                        ? -1L
                        : refreshTokenRepository
                                .findByTokenHash(TokenHash.sha256(currentRawRefreshToken))
                                .map(RefreshToken::getId)
                                .orElse(-1L);
        refreshTokenRepository.revokeAllExcept(memberId, keepId, now);
    }

    /** MY-11 ④ 탈퇴·MY-04 ③ 5번 틀림: 모든 기기 로그아웃 */
    @Transactional
    public void revokeAllDevices(Long memberId) {
        refreshTokenRepository.revokeAll(memberId, LocalDateTime.now());
    }

    /** AUTH-10 ④: 이미 가입한 카카오·구글 계정이면 바로 로그인 */
    @Transactional
    public LoginResult loginSocial(Long memberId) {
        Member member = memberService.getActiveMember(memberId);
        member.loggedIn(LocalDateTime.now());
        return issue(member, false);
    }

    /** AUTH-10 ⑤: 소셜 가입 완료 → 환영 알림·가입 완료 메일 → 바로 로그인 */
    @Transactional
    public LoginResult completeSocialSignup(Long memberId) {
        Member member = memberService.getActiveMember(memberId);
        emailOutboxService.enqueueSignupComplete(member);
        notificationService.send(
                member.getId(),
                NotificationType.WELCOME,
                null,
                null,
                null,
                member.getNickname() + "님, 환영해요! 프로필을 채우면 팀 초대를 더 많이 받아요",
                "/mypage/profile");
        return issue(member, false);
    }

    private void checkWithdrawn(Member member) {
        if (member.isWithdrawn()) {
            long daysLeft =
                    Math.max(
                            0,
                            ChronoUnit.DAYS.between(
                                    LocalDateTime.now().toLocalDate(),
                                    member.restoreDeadline().toLocalDate()));
            throw new BusinessException(
                    ErrorCode.ACCOUNT_WITHDRAWN, "탈퇴 신청한 계정이에요. 복원할까요? (" + daysLeft + "일 남음)");
        }
    }

    private RefreshToken findUsable(String rawRefreshToken, LocalDateTime now) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return refreshTokenRepository
                .findByTokenHash(TokenHash.sha256(rawRefreshToken))
                .filter(token -> token.isUsable(now))
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }

    private LoginResult issue(Member member, boolean rememberMe) {
        if (member.isSuspended())
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED, "이용 정지된 계정입니다");
        String rawRefresh = UUID.randomUUID() + "." + UUID.randomUUID();
        Duration refreshTtl = Duration.ofDays(rememberMe ? rememberMeDays : refreshDays);
        refreshTokenRepository.save(
                new RefreshToken(
                        member.getId(),
                        TokenHash.sha256(rawRefresh),
                        rememberMe,
                        LocalDateTime.now().plus(refreshTtl)));
        String accessToken = jwtProvider.createAccessToken(member.getId(), member.getRole());
        return new LoginResult(accessToken, rawRefresh, refreshTtl, rememberMe, member);
    }

    /** AUTH-02 ① 예: jj****@g****.com — TODO(확인 필요): 가림 범위 팀 협의 */
    static String maskEmail(String email) {
        int at = email.indexOf('@');
        String local = email.substring(0, at);
        String domain = email.substring(at + 1);
        int dot = domain.lastIndexOf('.');
        String domainName = dot > 0 ? domain.substring(0, dot) : domain;
        String tld = dot > 0 ? domain.substring(dot) : "";
        return keep(local, 2) + "@" + keep(domainName, 1) + tld;
    }

    private static String keep(String value, int visible) {
        int shown = Math.min(visible, value.length());
        return value.substring(0, shown) + "****";
    }
}
