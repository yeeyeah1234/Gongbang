package com.gongbang.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.gongbang.notification.domain.EmailOutbox;
import com.gongbang.notification.domain.NotificationType;
import com.gongbang.notification.domain.SendMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** 실제 공통 렌더러로 전체 메일을 보여 주는 샘플. SMTP 전송이나 실제 회원 조회는 하지 않는다. */
class EmailCatalogTest {
    private static final String BASE = "https://gongbang.example";

    private record Example(
            String type,
            String label,
            String message,
            String path,
            boolean security,
            boolean pending) {}

    private static final List<Example> EXAMPLES =
            List.of(
                    new Example(
                            "VERIFICATION_CODE",
                            "인증번호",
                            "인증번호: 012345\n\n3분 안에 입력해 주세요. 본인이 요청하지 않았다면 이 메일을 무시해 주세요.",
                            "",
                            true,
                            false),
                    new Example(
                            "SIGNUP_COMPLETE",
                            "가입 완료",
                            "공방회원님, 공방에 오신 걸 환영해요.\n관심 분야에 맞는 공모전과 팀원을 찾아보세요.",
                            "/",
                            true,
                            false),
                    new Example(
                            "LOGIN_LOCKED",
                            "로그인 잠금",
                            "비밀번호를 여러 번 틀려 30분 동안 로그인을 막았어요.\n본인이 아니라면 비밀번호를 바꿔 주세요.",
                            "/find-password",
                            true,
                            false),
                    new Example(
                            "PASSWORD_CHANGED",
                            "비밀번호 변경",
                            "비밀번호가 변경되었어요.\n본인이 아니라면 바로 문의해 주세요.",
                            "/",
                            true,
                            false),
                    new Example(
                            "WITHDRAWN",
                            "탈퇴 신청",
                            "탈퇴 신청이 완료됐어요 (복원 기한: 2026.12.26)\n기한 안에는 로그인 화면에서 계정을 복원할 수 있어요.",
                            "/login",
                            true,
                            false),
                    new Example(
                            "RESTORED", "계정 복원", "계정이 복원되었어요. 다시 만나서 반가워요.", "/login", true, false),
                    new Example(
                            "APPLY_NEW",
                            "새 지원",
                            "‘함께 만드는 디자인 공모전’ 모집글에 새로운 지원이 도착했어요.",
                            "/posts/1",
                            false,
                            false),
                    new Example(
                            "APPLY_ACCEPTED",
                            "지원 수락",
                            "‘함께 만드는 디자인 공모전’ 팀에 합류했어요.",
                            "/posts/1",
                            false,
                            false),
                    new Example(
                            "APPLY_REJECTED",
                            "지원 거절",
                            "‘함께 만드는 디자인 공모전’ 지원이 거절되었어요.",
                            "/posts/1",
                            false,
                            false),
                    new Example(
                            "POST_DELETED",
                            "지원한 모집글 삭제",
                            "지원한 모집글 ‘함께 만드는 디자인 공모전’이 삭제되어 모집이 마감됐어요.",
                            "/notifications",
                            false,
                            false),
                    new Example(
                            "TEAM_INVITE",
                            "팀 초대",
                            "공방회원님이 ‘함께 만드는 디자인 공모전’ 팀에 초대했어요.",
                            "/notifications",
                            false,
                            false),
                    new Example(
                            "COMMENT",
                            "새 댓글",
                            "내 글에 새로운 댓글이 달렸어요.\n공방에서 댓글 내용을 확인해 주세요.",
                            "/posts/1",
                            false,
                            false),
                    new Example("REPLY", "새 답글", "내 댓글에 새로운 답글이 달렸어요.", "/posts/1", false, false),
                    new Example(
                            "NOTICE_NEW",
                            "팀 공지",
                            "워크스페이스에 ‘이번 주 진행 일정’ 공지가 등록됐어요.",
                            "/notifications",
                            false,
                            false),
                    new Example(
                            "TEAM_DEADLINE",
                            "팀 공모전 마감",
                            "‘함께 만드는 디자인 공모전’ 마감이 7일 남았어요.",
                            "/notifications",
                            false,
                            false),
                    new Example(
                            "BOOKMARK_DEADLINE",
                            "북마크 공모전 마감",
                            "저장한 ‘아이디어 공모전’의 마감일이 다가왔어요.",
                            "/contests/1",
                            false,
                            false),
                    new Example(
                            "VERIFY_RESULT",
                            "인증 심사 결과",
                            "신청하신 자격증 인증이 승인됐어요.\n마이페이지에서 결과를 확인해 주세요.",
                            "/mypage/profile",
                            false,
                            false),
                    new Example(
                            "POLICY_NOTICE",
                            "정책 변경 공지",
                            "서비스 이용 정책 변경 안내가 등록됐어요.\n변경 내용과 적용 일자를 확인해 주세요.",
                            "/notifications",
                            false,
                            false),
                    new Example(
                            "NICKNAME_CHANGE_REQUIRED",
                            "닉네임 변경 요구",
                            "현재 닉네임을 변경해 주세요.\n마이페이지에서 안내 내용을 확인해 주세요.",
                            "/mypage/nickname",
                            false,
                            true),
                    new Example(
                            "ACCOUNT_SANCTION",
                            "계정 경고·이용 제한",
                            "계정 이용에 관한 운영 안내가 도착했어요.\n적용 사유와 기간을 확인해 주세요.",
                            "/notifications",
                            false,
                            true));

    @Test
    void 전체_메일에_로고와_본문을_적용하고_통합_미리보기를_만든다() throws Exception {
        EmailTemplateRenderer renderer = new EmailTemplateRenderer(BASE);
        Path directory = Path.of("build", "email-preview", "all");
        Files.createDirectories(directory);
        Files.write(
                directory.resolve("logo.png"),
                new ClassPathResource("mail/logo.png").getContentAsByteArray());
        assertThat(EXAMPLES.stream().filter(e -> !e.pending()).count()).isEqualTo(18);
        assertThat(EXAMPLES.stream().map(Example::type).distinct().count()).isEqualTo(20);
        StringBuilder links = new StringBuilder();
        int number = 0;
        for (Example e : EXAMPLES) {
            String body = e.message();
            if (!e.security()) {
                NotificationType type = NotificationType.valueOf(e.type());
                body +=
                        "\n\n바로 가기: "
                                + BASE
                                + e.path()
                                + "\n\n[알림 수신 설정] "
                                + BASE
                                + "/mypage/settings?tab=notification"
                                + (type.getSettingType() == null
                                        ? "\n이 메일은 필수 안내로 수신을 끌 수 없습니다."
                                        : "\n[이 종류 메일 끄기] "
                                                + BASE
                                                + "/mypage/settings?tab=notification&off="
                                                + type.getSettingType().name());
            }
            String subject = "[공방] " + e.label();
            EmailOutbox mail =
                    new EmailOutbox(
                            1L,
                            "preview@example.com",
                            e.type(),
                            e.security(),
                            SendMode.IMMEDIATE,
                            null,
                            subject,
                            body,
                            LocalDateTime.of(2026, 11, 26, 9, 0));
            String html = renderer.render(mail, true);
            assertThat(html)
                    .as(e.type())
                    .contains("cid:gongbang-logo", "alt=\"공방 · 공모전 작업방\"")
                    .doesNotContain("{{", "null");
            if (!e.security())
                assertThat(html).contains("href=\"" + BASE + e.path() + "\"", "알림 수신 설정");
            assertThat(mail.getBody()).isEqualTo(body);
            String file = String.format("%02d-%s.html", ++number, e.type().toLowerCase());
            Files.writeString(
                    directory.resolve(file), html.replace("cid:gongbang-logo", "logo.png"));
            links.append("<a target=\"mail-preview\" href=\"")
                    .append(file)
                    .append("\">")
                    .append(String.format("%02d", number))
                    .append(" &nbsp; ")
                    .append(e.label())
                    .append(e.pending() ? " <small>미병합</small>" : "")
                    .append("</a>");
        }
        String gallery =
                new ClassPathResource("mail-preview/catalog.html")
                        .getContentAsString(StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("index.html"), gallery.replace("{{links}}", links));
    }
}
