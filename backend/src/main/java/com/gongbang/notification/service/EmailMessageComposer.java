package com.gongbang.notification.service;

import com.gongbang.notification.domain.EmailOutbox;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/** 저장된 평문을 유지하면서 모든 발송 메일에 HTML 대안을 함께 제공한다. */
@Component
public class EmailMessageComposer {
    private final EmailTemplateRenderer renderer;
    private final Resource logo;

    public EmailMessageComposer(
            EmailTemplateRenderer renderer,
            @Value("${app.mail-logo:classpath:mail/logo.png}") Resource logo) {
        this.renderer = renderer;
        this.logo = logo;
    }

    public MimeMessage compose(JavaMailSender sender, String from, EmailOutbox mail)
            throws MessagingException {
        MimeMessage message = sender.createMimeMessage();
        boolean hasLogo = logo.exists() && logo.isReadable();
        MimeMessageHelper helper =
                new MimeMessageHelper(
                        message,
                        MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                        StandardCharsets.UTF_8.name());
        helper.setFrom(from);
        helper.setTo(mail.getToEmail());
        helper.setSubject(mail.getSubject());
        helper.setText(mail.getBody(), renderer.render(mail, hasLogo));
        if (hasLogo) {
            // PNG 원본은 메일에 포함하므로 별도 공개 이미지 서버가 필요하지 않다.
            helper.addInline("gongbang-logo", logo, "image/png");
        }
        return message;
    }
}
