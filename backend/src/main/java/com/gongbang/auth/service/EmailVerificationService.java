package com.gongbang.auth.service;

import com.gongbang.auth.domain.EmailVerification;
import com.gongbang.auth.domain.VerificationPurpose;
import com.gongbang.auth.dto.SendCodeResponse;
import com.gongbang.auth.jwt.JwtProvider;
import com.gongbang.auth.repository.EmailVerificationRepository;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.service.MemberService;
import com.gongbang.notification.service.EmailOutboxService;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** AUTH-05 이메일 인증번호 (가입·로그인 추가 인증·비밀번호 재설정·계정 복원 공통) */
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    /** AUTH-05 ② 3분 타이머, 재발송은 30초 뒤 */
    public static final int CODE_VALID_MINUTES = 3;

    public static final int RESEND_SECONDS = 30;

    /** AUTH-05 ③ 같은 이메일 1시간 5번 */
    public static final int SEND_LIMIT_PER_HOUR = 5;

    private static final String TOKEN_PREFIX = "verified:";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final EmailVerificationRepository verificationRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final EmailOutboxService emailOutboxService;
    private final MemberService memberService;

    /** 메일 서버가 없을 때 인증번호를 응답에 실어 화면에 보여 줄지 (로컬 개발용, 기본 꺼짐) */
    @Value("${app.mail-dev-code:false}")
    private boolean devCodeEnabled;

    @Transactional
    public SendCodeResponse sendCode(
            String rawEmail, VerificationPurpose purpose, boolean newSignup) {
        String email = normalize(rawEmail);
        LocalDateTime now = LocalDateTime.now();
        if (purpose == VerificationPurpose.SIGNUP) {
            // AUTH-05 ⑥⑦: 이미 가입·탈퇴 신청한 이메일 안내
            memberService.checkEmailAvailableForSignup(email, newSignup);
        } else {
            memberService.checkEmailRegistered(email, purpose);
        }

        long sentInHour =
                verificationRepository.countByEmailAndPurposeAndCreatedAtAfter(
                        email, purpose, now.minusHours(1));
        if (sentInHour >= SEND_LIMIT_PER_HOUR) {
            throw new BusinessException(
                    ErrorCode.TOO_MANY_REQUESTS, "요청이 너무 많아요. 1시간 뒤에 다시 시도해 주세요");
        }
        verificationRepository
                .findTopByEmailAndPurposeOrderByIdDesc(email, purpose)
                .filter(last -> last.getCreatedAt().plusSeconds(RESEND_SECONDS).isAfter(now))
                .ifPresent(
                        last -> {
                            throw new BusinessException(
                                    ErrorCode.TOO_MANY_REQUESTS, "인증번호는 30초 뒤에 다시 받을 수 있어요");
                        });

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        EmailVerification verification =
                verificationRepository.save(
                        new EmailVerification(
                                email,
                                purpose,
                                passwordEncoder.encode(code),
                                now,
                                CODE_VALID_MINUTES));
        LocalDateTime resendAt = now.plusSeconds(RESEND_SECONDS);
        if (devCodeEnabled && !emailOutboxService.isMailConfigured()) {
            // 로컬 개발: 메일 서버가 없으면 메일 대신 화면에 인증번호를 보여 준다 (배포 때는 MAIL_DEV_CODE=false)
            return new SendCodeResponse(verification.getExpiresAt(), resendAt, code);
        }
        // 인증번호는 3분짜리라 대기열에 넣고 바로 보낸다. 실패하면 AUTH-05 ④ 문구
        boolean sent = emailOutboxService.sendVerificationCodeNow(email, code);
        if (!sent) {
            throw new BusinessException(ErrorCode.MAIL_SEND_FAILED);
        }
        return new SendCodeResponse(verification.getExpiresAt(), resendAt, null);
    }

    /** 맞으면 다음 단계에 쓸 verificationToken (가입 30분, 나머지 10분) */
    @Transactional(noRollbackFor = BusinessException.class)
    public String verify(String rawEmail, VerificationPurpose purpose, String code) {
        String email = normalize(rawEmail);
        LocalDateTime now = LocalDateTime.now();
        EmailVerification verification =
                verificationRepository
                        .findTopByEmailAndPurposeOrderByIdDesc(email, purpose)
                        .orElseThrow(() -> new BusinessException(ErrorCode.CODE_EXPIRED));
        if (verification.isLocked()) {
            throw new BusinessException(ErrorCode.CODE_LOCKED);
        }
        if (verification.isExpired(now)) {
            throw new BusinessException(ErrorCode.CODE_EXPIRED);
        }
        if (code == null || !passwordEncoder.matches(code, verification.getCodeHash())) {
            int remaining = verification.fail();
            if (remaining <= 0) {
                throw new BusinessException(ErrorCode.CODE_LOCKED);
            }
            throw new BusinessException(
                    ErrorCode.CODE_MISMATCH, "인증번호가 맞지 않아요 (남은 횟수 " + remaining + "번)");
        }
        verification.verify(now);
        return jwtProvider.createPurposeToken(
                TOKEN_PREFIX + purpose.name(),
                email,
                Duration.ofMinutes(purpose.getValidMinutesAfterVerify()));
    }

    /** verificationToken을 확인하고 이메일을 돌려준다. 만료면 400 TOKEN_EXPIRED */
    public String requireVerifiedEmail(
            String token, VerificationPurpose purpose, String expectedEmail) {
        String email =
                jwtProvider
                        .parsePurposeToken(token, TOKEN_PREFIX + purpose.name())
                        .orElseThrow(() -> new BusinessException(ErrorCode.TOKEN_EXPIRED));
        if (expectedEmail != null && !email.equals(normalize(expectedEmail))) {
            // AUTH-05 ⑤: 인증 뒤 이메일을 고치면 다시 인증
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        }
        return email;
    }

    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }
}
