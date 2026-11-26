package com.gongbang.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gongbang.notification.domain.EmailOutbox;
import com.gongbang.notification.repository.EmailOutboxRepository;
import com.gongbang.notification.repository.NotificationRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

class EmailDispatcherTest {
    @SuppressWarnings("unchecked")
    private EmailDispatcher dispatcher(JavaMailSender sender, EmailMessageComposer composer) {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        EmailDispatcher dispatcher =
                new EmailDispatcher(
                        mock(EmailOutboxRepository.class),
                        mock(NotificationRepository.class),
                        provider,
                        composer);
        ReflectionTestUtils.setField(dispatcher, "mailHost", "localhost");
        ReflectionTestUtils.setField(dispatcher, "smtpAuth", false);
        return dispatcher;
    }

    @Test
    void HTML발송성공시에만_SENT로_바꾼다() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        EmailMessageComposer composer = mock(EmailMessageComposer.class);
        when(composer.compose(any(), anyString(), any()))
                .thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        EmailOutbox mail = EmailTemplateRendererTest.mail("PASSWORD_CHANGED", true, "변경 완료");
        assertThat(dispatcher(sender, composer).dispatch(mail)).isTrue();
        assertThat(mail.getStatus()).isEqualTo(EmailOutbox.Status.SENT);
        assertThat(mail.getSentAt()).isNotNull();
    }

    @Test
    void MIME생성실패는_발송실패로_기록하고_재시도정책을_유지한다() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        EmailMessageComposer composer = mock(EmailMessageComposer.class);
        when(composer.compose(any(), anyString(), any()))
                .thenThrow(new MessagingException("invalid"));
        EmailOutbox mail = EmailTemplateRendererTest.mail("PASSWORD_CHANGED", true, "변경 완료");
        EmailDispatcher dispatcher = dispatcher(sender, composer);
        for (int i = 0; i < EmailOutbox.MAX_RETRY; i++)
            assertThat(dispatcher.dispatch(mail)).isFalse();
        assertThat(mail.getStatus()).isEqualTo(EmailOutbox.Status.FAILED);
        assertThat(mail.getRetryCount()).isEqualTo(EmailOutbox.MAX_RETRY);
        assertThat(mail.getSentAt()).isNull();
    }

    @Test
    void SMTP실패시_발송완료로_기록하지_않는다() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        EmailMessageComposer composer = mock(EmailMessageComposer.class);
        when(composer.compose(any(), anyString(), any()))
                .thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        doThrow(new MailSendException("offline")).when(sender).send(any(MimeMessage.class));
        EmailOutbox mail = EmailTemplateRendererTest.mail("PASSWORD_CHANGED", true, "변경 완료");
        assertThat(dispatcher(sender, composer).dispatch(mail)).isFalse();
        assertThat(mail.getStatus()).isEqualTo(EmailOutbox.Status.PENDING);
        assertThat(mail.getRetryCount()).isEqualTo(1);
        assertThat(mail.getSentAt()).isNull();
    }
}
