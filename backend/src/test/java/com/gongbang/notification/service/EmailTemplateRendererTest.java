package com.gongbang.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.gongbang.notification.domain.EmailOutbox;
import com.gongbang.notification.domain.NotificationType;
import com.gongbang.notification.domain.SendMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class EmailTemplateRendererTest {
    private static final String BASE = "https://gongbang.example";
    private final EmailTemplateRenderer renderer = new EmailTemplateRenderer(BASE);

    EmailTemplateRendererTest() throws Exception {}

    static EmailOutbox mail(String type, boolean security, String body) {
        return new EmailOutbox(
                1L,
                "test@example.com",
                type,
                security,
                SendMode.IMMEDIATE,
                null,
                switch (type) {
                    case "VERIFICATION_CODE" -> "[공방] 인증번호를 확인해 주세요";
                    case "LOGIN_LOCKED" -> "[공방] 로그인 시도가 반복됐어요";
                    case "COMMENT" -> "[공방] 새 댓글이 도착했어요";
                    default -> "[공방] 안내를 확인해 주세요";
                },
                body,
                LocalDateTime.of(2026, 11, 26, 9, 0));
    }

    private static String notificationBody(String message) {
        return message
                + "\n\n바로 가기: "
                + BASE
                + "/notifications/1"
                + "\n\n[알림 수신 설정] "
                + BASE
                + "/mypage/settings?tab=notification"
                + "\n[이 종류 메일 끄기] "
                + BASE
                + "/mypage/settings?tab=notification&off=COMMENT";
    }

    @Test
    void 인증번호는_큰_카드로_표시하고_유효시간과_원문은_유지한다() {
        EmailOutbox mail = mail("VERIFICATION_CODE", true, "인증번호: 012345\n\n3분 안에 입력해 주세요.");
        String html = renderer.render(mail, false);
        assertThat(html).contains("font-size:36px", "012345", "3분 안에 입력", "공방");
        assertThat(html).doesNotContain("cid:", "href=", "{{");
        assertThat(mail.getBody()).isEqualTo("인증번호: 012345\n\n3분 안에 입력해 주세요.");
    }

    @Test
    void 모든_알림종류에_공통디자인과_안전한_이동설정링크를_적용한다() {
        for (NotificationType type : NotificationType.values()) {
            String html =
                    renderer.render(mail(type.name(), false, notificationBody("새 소식이 있어요")), false);
            assertThat(html)
                    .contains("새 소식이 있어요", "자세히 확인하기", "알림 수신 설정", "이 종류 메일 끄기")
                    .contains("&amp;off=COMMENT")
                    .doesNotContain("{{");
        }
    }

    @Test
    void 필수안내는_수신해제버튼_없이_기존안내를_유지한다() {
        String body =
                "승인되었습니다.\n\n바로 가기: "
                        + BASE
                        + "/mypage"
                        + "\n\n[알림 수신 설정] "
                        + BASE
                        + "/mypage/settings"
                        + "\n이 메일은 필수 안내로 수신을 끌 수 없습니다.";
        assertThat(renderer.render(mail("VERIFY_RESULT", false, body), false))
                .contains("수신을 끌 수 없습니다.", "자세히 확인하기")
                .doesNotContain("이 종류 메일 끄기");
    }

    @Test
    void 사용자_본문과_제목의_HTML은_실행되지_않고_외부링크는_버튼으로_만들지_않는다() {
        String html =
                renderer.render(
                        mail(
                                "COMMENT",
                                false,
                                notificationBody("<script>alert(1)</script> {{brand}} & \"테스트\"")
                                        .replace(
                                                BASE + "/notifications/1",
                                                "https://evil.example/path")),
                        false);
        assertThat(html)
                .contains("&lt;script&gt;", "&amp;", "{{brand}}")
                .doesNotContain("<script>", "href=", "자세히 확인하기");
    }

    @Test
    void 잘못된_URL과_인증번호_형식도_평문내용을_잃지_않는다() {
        for (String unsafe :
                new String[] {
                    "javascript:alert(1)",
                    "https://gongbang.example@evil.example/",
                    "https://gongbang.example:444/"
                }) {
            assertThat(
                            renderer.render(
                                    mail(
                                            "COMMENT",
                                            false,
                                            notificationBody("안내")
                                                    .replace(BASE + "/notifications/1", unsafe)),
                                    false))
                    .doesNotContain("href=");
        }
        assertThat(renderer.render(mail("VERIFICATION_CODE", true, "이전 형식의 인증 안내"), false))
                .contains("이전 형식의 인증 안내");
    }

    @Test
    void 실제_렌더러로_인증번호_알림_보안메일_미리보기를_생성한다() throws Exception {
        Path directory = Path.of("build", "email-preview");
        Files.createDirectories(directory);
        Files.writeString(
                directory.resolve("verification.html"),
                renderer.render(
                        mail(
                                "VERIFICATION_CODE",
                                true,
                                "인증번호: 012345\n\n3분 안에 입력해 주세요. 본인이 요청하지 않았다면 이 메일을 무시해 주세요."),
                        true));
        Files.writeString(
                directory.resolve("notification.html"),
                renderer.render(
                        mail(
                                "COMMENT",
                                false,
                                notificationBody("새 댓글이 도착했어요.\n공방에서 이야기를 이어가 보세요.")),
                        true));
        Files.writeString(
                directory.resolve("security.html"),
                renderer.render(
                        mail(
                                "LOGIN_LOCKED",
                                true,
                                "비밀번호를 여러 번 틀려 30분 동안 로그인을 막았어요.\n본인이 아니라면 비밀번호를 바꿔 주세요."),
                        true));
    }
}
