package com.gongbang.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSenderImpl;

class EmailMessageComposerTest {
    @Test
    void 로고가_없어도_UTF8_평문과_HTML을_함께_발송할수있다() throws Exception {
        EmailMessageComposer composer =
                new EmailMessageComposer(
                        new EmailTemplateRenderer("https://gongbang.example"),
                        new ClassPathResource("mail/not-present.png"));
        MimeMessage message =
                composer.compose(
                        new JavaMailSenderImpl(),
                        "공방 <mail@example.com>",
                        EmailTemplateRendererTest.mail(
                                "VERIFICATION_CODE", true, "인증번호: 012345\n\n3분 안에 입력해 주세요."));
        message.saveChanges();
        List<Part> parts = flatten(message);
        assertThat(parts.stream().filter(p -> mime(p, "text/plain")).count()).isEqualTo(1);
        assertThat(parts.stream().filter(p -> mime(p, "text/html")).count()).isEqualTo(1);
        assertThat(message.getSubject()).isEqualTo("[공방] 인증번호를 확인해 주세요");
        Part plain = parts.stream().filter(p -> mime(p, "text/plain")).findFirst().orElseThrow();
        assertThat(plain.getContent()).isEqualTo("인증번호: 012345\n\n3분 안에 입력해 주세요.");
        assertThat(plain.getContentType()).containsIgnoringCase("charset=UTF-8");
    }

    @Test
    void 로고가_있으면_CID이미지와_HTML을_연결한다() throws Exception {
        ClassPathResource logo = new ClassPathResource("mail/logo.png");
        assertThat(logo.exists()).isTrue();
        EmailMessageComposer composer =
                new EmailMessageComposer(
                        new EmailTemplateRenderer("https://gongbang.example"), logo);
        MimeMessage message =
                composer.compose(
                        new JavaMailSenderImpl(),
                        "mail@example.com",
                        EmailTemplateRendererTest.mail("PASSWORD_CHANGED", true, "비밀번호가 변경되었어요."));
        message.saveChanges();
        List<Part> parts = flatten(message);
        Part image = parts.stream().filter(p -> mime(p, "image/png")).findFirst().orElseThrow();
        assertThat(image.getHeader("Content-ID")).containsExactly("<gongbang-logo>");
        assertThat(image.getDisposition()).isEqualTo(Part.INLINE);
        try (var attached = image.getInputStream();
                var original = logo.getInputStream()) {
            assertThat(attached.readAllBytes()).isEqualTo(original.readAllBytes());
        }
        Part html = parts.stream().filter(p -> mime(p, "text/html")).findFirst().orElseThrow();
        assertThat(html.getContent().toString())
                .contains("cid:gongbang-logo", "alt=\"공방 · 공모전 작업방\"", "height:auto");
    }

    private static boolean mime(Part part, String type) {
        try {
            return part.isMimeType(type);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static List<Part> flatten(Part part) throws Exception {
        List<Part> result = new ArrayList<>();
        result.add(part);
        if (part.getContent() instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++)
                result.addAll(flatten(multipart.getBodyPart(i)));
        }
        return result;
    }
}
