package com.gongbang.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gongbang.auth.domain.EmailVerification;
import com.gongbang.auth.domain.VerificationPurpose;
import com.gongbang.auth.dto.SendCodeResponse;
import com.gongbang.auth.jwt.JwtProvider;
import com.gongbang.auth.repository.EmailVerificationRepository;
import com.gongbang.member.service.MemberService;
import com.gongbang.notification.service.EmailOutboxService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/** AUTH-05 회원가입 2단계: 메일 서버가 없어도 인증번호를 받아 인증까지 갈 수 있어야 한다 */
class EmailVerificationServiceTest {

    private EmailVerificationRepository repository;
    private EmailOutboxService outbox;
    private JwtProvider jwtProvider;
    private EmailVerificationService service;
    private EmailVerification saved;

    @BeforeEach
    void setUp() {
        repository = mock(EmailVerificationRepository.class);
        outbox = mock(EmailOutboxService.class);
        jwtProvider = mock(JwtProvider.class);
        service =
                new EmailVerificationService(
                        repository,
                        new BCryptPasswordEncoder(),
                        jwtProvider,
                        outbox,
                        mock(MemberService.class));
        ReflectionTestUtils.setField(service, "devCodeEnabled", true);
        when(repository.save(any(EmailVerification.class)))
                .thenAnswer(
                        inv -> {
                            saved = inv.getArgument(0);
                            return saved;
                        });
        when(repository.findTopByEmailAndPurposeOrderByIdDesc(anyString(), any()))
                .thenAnswer(inv -> Optional.ofNullable(saved));
        when(jwtProvider.createPurposeToken(anyString(), anyString(), any())).thenReturn("token");
    }

    @Test
    void 메일_서버가_없으면_인증번호를_화면에_주고_그_번호로_인증된다() {
        when(outbox.isMailConfigured()).thenReturn(false);

        SendCodeResponse res =
                service.sendCode("Test@Email.com ", VerificationPurpose.SIGNUP, false);

        assertThat(res.devCode()).matches("\\d{6}");
        verify(outbox, never()).sendVerificationCodeNow(anyString(), anyString());
        assertThat(service.verify("test@email.com", VerificationPurpose.SIGNUP, res.devCode()))
                .isEqualTo("token");
    }

    @Test
    void 메일_서버가_있으면_메일로_보내고_화면에는_번호를_주지_않는다() {
        when(outbox.isMailConfigured()).thenReturn(true);
        when(outbox.sendVerificationCodeNow(eq("test@email.com"), anyString())).thenReturn(true);

        SendCodeResponse res = service.sendCode("test@email.com", VerificationPurpose.SIGNUP, false);

        assertThat(res.devCode()).isNull();
        verify(outbox).sendVerificationCodeNow(eq("test@email.com"), anyString());
    }

    @Test
    void 배포에서_끄면_메일_서버가_없을_때_번호를_주지_않는다() {
        ReflectionTestUtils.setField(service, "devCodeEnabled", false);
        when(outbox.isMailConfigured()).thenReturn(false);
        when(outbox.sendVerificationCodeNow(anyString(), anyString())).thenReturn(false);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> service.sendCode("test@email.com", VerificationPurpose.SIGNUP, false))
                .isInstanceOf(com.gongbang.global.error.exception.BusinessException.class);
    }
}
