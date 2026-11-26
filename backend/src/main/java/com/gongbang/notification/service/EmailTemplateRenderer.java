package com.gongbang.notification.service;

import com.gongbang.notification.domain.EmailOutbox;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/** AUTH-05 · NOTI-06: 기존 대기열 평문에도 적용하는 공통 메일 디자인. */
@Component
public class EmailTemplateRenderer {
    private static final Pattern SLOT = Pattern.compile("\\{\\{([a-z]+)}}");
    private static final Pattern CODE = Pattern.compile("\\A인증번호: ([0-9]{6})\\n\\n");
    // NotificationOutbox의 마지막 안내 블록만 버튼으로 바꾼다. 본문의 임의 링크는 활성화하지 않는다.
    private static final Pattern NOTICE_LINKS =
            Pattern.compile(
                    "\\n\\n바로 가기: ([^\\r\\n]+)\\n\\n\\[알림 수신 설정] ([^\\r\\n]+)"
                            + "(?:\\n이 메일은 필수 안내로 수신을 끌 수 없습니다.|\\n\\[이 종류 메일 끄기] ([^\\r\\n]+))\\z");
    private final String template;
    private final String frontBaseUrl;

    public EmailTemplateRenderer(
            @Value("${app.front-base-url:http://localhost:5173}") String frontBaseUrl)
            throws IOException {
        this.frontBaseUrl = frontBaseUrl.replaceAll("/+$", "");
        this.template =
                new ClassPathResource("mail/layout.html")
                        .getContentAsString(StandardCharsets.UTF_8);
    }

    public String render(EmailOutbox mail, boolean hasLogo) {
        String body = mail.getBody();
        String codeCard = "";
        String action = "";
        String footer = mail.isSecurity() ? "계정 보호를 위한 필수 안내 메일입니다." : "공방에서 새로운 소식을 확인해 주세요.";
        if ("VERIFICATION_CODE".equals(mail.getMailType())) {
            Matcher code = CODE.matcher(body);
            if (code.find()) {
                codeCard =
                        "<div style=\"margin:0 0 24px;padding:24px 12px;background:#fff8f2;border:1px solid #f9dcc9;border-radius:8px;text-align:center\">"
                                + "<p style=\"margin:0 0 12px;color:#8b6b56;font-size:12px;letter-spacing:1px\">이메일 인증번호</p>"
                                + "<strong style=\"font-family:Arial,sans-serif;font-size:36px;letter-spacing:8px;color:#202b3b\">"
                                + code.group(1)
                                + "</strong><p style=\"margin:16px 0 0;font-size:12px;color:#a54d24\">발급 후 3분 동안 유효해요</p></div>";
                body = body.substring(code.end());
            }
        } else if (!mail.isSecurity()) {
            Matcher links = NOTICE_LINKS.matcher(body);
            if (links.find()
                    && safeUrl(links.group(1))
                    && safeUrl(links.group(2))
                    && (links.group(3) == null || safeUrl(links.group(3)))) {
                body = body.substring(0, links.start());
                action = button(links.group(1), "자세히 확인하기");
                footer =
                        anchor(links.group(2), "알림 수신 설정")
                                + (links.group(3) == null
                                        ? "<br>이 메일은 필수 안내로 수신을 끌 수 없습니다."
                                        : " &nbsp;·&nbsp; " + anchor(links.group(3), "이 종류 메일 끄기"));
            }
        }
        if (mail.isSecurity() && !"VERIFICATION_CODE".equals(mail.getMailType())) {
            String path =
                    switch (mail.getMailType()) {
                        case "LOGIN_LOCKED" -> "/find-password";
                        case "WITHDRAWN", "RESTORED" -> "/login";
                        default -> "/";
                    };
            String label = "LOGIN_LOCKED".equals(mail.getMailType()) ? "비밀번호 재설정" : "공방으로 이동";
            if (safeUrl(frontBaseUrl + path)) action = button(frontBaseUrl + path, label);
        }
        String title = mail.getSubject().replaceFirst("^\\[공방]\\s*", "");
        String brand =
                hasLogo
                        ? "<img src=\"cid:gongbang-logo\" alt=\"공방 · 공모전 작업방\" width=\"128\" style=\"display:block;width:128px;max-width:100%;height:auto;border:0\">"
                        : "<span style=\"font-size:24px;font-weight:700;color:#1b2d45\">공방</span>";
        Map<String, String> slots =
                Map.of(
                        "title",
                        escape(title),
                        "heading",
                        "VERIFICATION_CODE".equals(mail.getMailType())
                                ? "인증번호를<br>입력해 주세요."
                                : escape(title),
                        "brand",
                        brand,
                        "category",
                        category(mail),
                        "code",
                        codeCard,
                        "body",
                        escape(body).replace("\n", "<br>"),
                        "action",
                        action,
                        "footer",
                        footer);
        Matcher matcher = SLOT.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find())
            matcher.appendReplacement(
                    result, Matcher.quoteReplacement(slots.get(matcher.group(1))));
        matcher.appendTail(result);
        return result.toString();
    }

    private static String category(EmailOutbox mail) {
        return switch (mail.getMailType()) {
            case "VERIFICATION_CODE" -> "이메일 인증";
            case "SIGNUP_COMPLETE" -> "가입 완료";
            case "LOGIN_LOCKED", "PASSWORD_CHANGED" -> "계정 보안";
            case "WITHDRAWN", "RESTORED" -> "계정 안내";
            case "APPLY_NEW", "APPLY_ACCEPTED", "APPLY_REJECTED" -> "지원 소식";
            case "POST_DELETED" -> "모집글 안내";
            case "TEAM_INVITE" -> "팀 초대";
            case "COMMENT", "REPLY" -> "댓글 소식";
            case "NOTICE_NEW" -> "워크스페이스 공지";
            case "TEAM_DEADLINE", "BOOKMARK_DEADLINE" -> "공모전 마감 안내";
            case "VERIFY_RESULT" -> "인증 심사 결과";
            case "POLICY_NOTICE" -> "서비스 정책 안내";
            case "NICKNAME_CHANGE_REQUIRED", "ACCOUNT_SANCTION" -> "계정 이용 안내";
            default -> mail.isSecurity() ? "계정 안내" : "공방 알림";
        };
    }

    private boolean safeUrl(String value) {
        try {
            URI base = URI.create(frontBaseUrl);
            URI url = URI.create(value);
            return ("https".equalsIgnoreCase(url.getScheme())
                            || "http".equalsIgnoreCase(url.getScheme()))
                    && url.getHost() != null
                    && url.getHost().equalsIgnoreCase(base.getHost())
                    && url.getScheme().equalsIgnoreCase(base.getScheme())
                    && url.getPort() == base.getPort()
                    && url.getUserInfo() == null
                    && value.chars().noneMatch(c -> c < 32 || c == 127);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String button(String url, String label) {
        return "<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"margin:24px 0 0\"><tr><td align=\"center\" bgcolor=\"#202b3b\" style=\"border-radius:6px;mso-padding-alt:16px 24px\">"
                + "<a href=\""
                + escape(url)
                + "\" style=\"display:block;padding:16px 24px;font-weight:700;font-size:15px;color:#ffffff;text-decoration:none\">"
                + label
                + " &rarr;</a></td></tr></table>";
    }

    private static String anchor(String url, String label) {
        return "<a href=\""
                + escape(url)
                + "\" style=\"color:#526175;text-decoration:underline\">"
                + label
                + "</a>";
    }

    private static String escape(String value) {
        return HtmlUtils.htmlEscape(value, StandardCharsets.UTF_8.name());
    }
}
