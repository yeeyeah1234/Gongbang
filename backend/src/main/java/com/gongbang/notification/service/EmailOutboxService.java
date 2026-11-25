package com.gongbang.notification.service;

import com.gongbang.member.domain.Member;
import com.gongbang.notification.domain.EmailOutbox;
import com.gongbang.notification.domain.NotificationType;
import com.gongbang.notification.domain.SendMode;
import com.gongbang.notification.repository.EmailOutboxRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * NOTI-06 메일 대기열에 넣는 곳 (AGENTS 8: 바로 SMTP 호출 금지 → email_outbox). 1분마다 EmailDispatcher가 보낸다. 계정 보안
 * 메일은 is_security = TRUE (하루 10통·야간 보류 제외, ⑤).
 */
@Service
@RequiredArgsConstructor
public class EmailOutboxService {

    private static final LocalTime NIGHT_START = LocalTime.of(22, 0);
    private static final LocalTime MORNING = LocalTime.of(8, 0);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    private final EmailOutboxRepository outboxRepository;
    private final EmailDispatcher emailDispatcher;

    @Value("${app.front-base-url:http://localhost:5173}")
    private String frontBaseUrl;

    // ---------- 계정 보안 메일 (항상 보냄) ----------

    /** 메일 서버(MAIL_HOST)가 설정돼 있는지 */
    public boolean isMailConfigured() {
        return emailDispatcher.isMailConfigured();
    }

    /** AUTH-05: 인증번호는 3분짜리라 대기열에 넣고 그 자리에서 보낸다. 실패하면 false */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean sendVerificationCodeNow(String email, String code) {
        EmailOutbox mail =
                outboxRepository.save(
                        new EmailOutbox(
                                null,
                                email,
                                "VERIFICATION_CODE",
                                true,
                                SendMode.IMMEDIATE,
                                null,
                                "[공방] 인증번호를 확인해 주세요",
                                "인증번호: " + code + "\n\n3분 안에 입력해 주세요. 본인이 요청하지 않았다면 이 메일을 무시해 주세요.",
                                LocalDateTime.now()));
        boolean sent = emailDispatcher.dispatch(mail);
        if (!sent) {
            // 실패한 인증번호 메일은 1분 뒤 다시 보내지 않는다 (그 번호는 이미 무효라 헷갈림)
            mail.cancel();
        }
        return sent;
    }

    public void enqueueSignupComplete(Member member) {
        security(
                member,
                "SIGNUP_COMPLETE",
                "[공방] 가입이 완료되었어요",
                member.getNickname()
                        + "님, 공방에 오신 걸 환영해요.\n관심 분야에 맞는 공모전과 팀원을 찾아보세요.\n\n"
                        + frontBaseUrl);
    }

    /** AUTH-01 ④ */
    public void enqueueLoginLocked(Member member) {
        security(
                member,
                "LOGIN_LOCKED",
                "[공방] 로그인 시도가 반복됐어요",
                "비밀번호를 여러 번 틀려 30분 동안 로그인을 막았어요.\n본인이 아니라면 비밀번호를 바꿔 주세요: "
                        + frontBaseUrl
                        + "/find-password");
    }

    /** AUTH-02 ⑤, MY-04 ⑤ */
    public void enqueuePasswordChanged(Member member) {
        security(
                member,
                "PASSWORD_CHANGED",
                "[공방] 비밀번호가 변경되었어요",
                "비밀번호가 변경되었어요.\n본인이 아니라면 바로 문의해 주세요.");
    }

    /** MY-11 ④ */
    public void enqueueWithdrawn(Member member) {
        security(
                member,
                "WITHDRAWN",
                "[공방] 탈퇴 신청이 완료됐어요",
                "탈퇴 신청이 완료됐어요 (복원 기한: "
                        + member.restoreDeadline().format(DATE)
                        + ")\n"
                        + "기한 안에는 로그인 화면에서 계정을 복원할 수 있어요.");
    }

    public void enqueueRestored(Member member) {
        security(member, "RESTORED", "[공방] 계정이 복원되었어요", "계정이 복원되었어요. 다시 만나서 반가워요.");
    }

    // ---------- 알림 메일 (NotificationService가 부름) ----------

    public void enqueueNotification(
            Member receiver,
            NotificationType type,
            Long notificationId,
            String message,
            String linkUrl,
            LocalDateTime now) {
        SendMode mode = type.getSendMode();
        LocalDateTime scheduledAt =
                switch (mode) {
                        // ② 댓글은 1시간 동안 안 읽은 것만 (발송 때 읽었으면 취소)
                    case HOURLY_DIGEST -> now.plusHours(1);
                        // ③ 하루 1번 오전 9시
                    case DAILY_9AM -> next9am(now);
                    case IMMEDIATE -> now;
                };
        scheduledAt = avoidNight(scheduledAt);
        String body =
                message
                        + "\n\n바로 가기: "
                        + frontBaseUrl
                        + linkUrl
                        // ⑦ 모든 메일 아래 링크
                        + "\n\n[알림 수신 설정] "
                        + frontBaseUrl
                        + "/mypage/settings?tab=notification"
                        + (type.getSettingType() == null
                                ? "\n이 메일은 필수 안내로 수신을 끌 수 없습니다."
                                : "\n[이 종류 메일 끄기] "
                                        + frontBaseUrl
                                        + "/mypage/settings?tab=notification&off="
                                        + type.getSettingType().name());
        outboxRepository.save(
                new EmailOutbox(
                        receiver.getId(),
                        receiver.getEmail(),
                        type.name(),
                        false,
                        mode,
                        notificationId,
                        "[공방] " + abbreviate(message, 60),
                        body,
                        scheduledAt));
    }

    private void security(Member member, String type, String subject, String body) {
        if (member.getEmail() == null) {
            return;
        }
        outboxRepository.save(
                new EmailOutbox(
                        member.getId(),
                        member.getEmail(),
                        type,
                        true,
                        SendMode.IMMEDIATE,
                        null,
                        subject,
                        body,
                        LocalDateTime.now()));
    }

    /** ⑥ 밤 10시~오전 8시에 생긴 메일은 오전 8시에 */
    static LocalDateTime avoidNight(LocalDateTime at) {
        LocalTime time = at.toLocalTime();
        if (!time.isBefore(NIGHT_START)) {
            return LocalDateTime.of(at.toLocalDate().plusDays(1), MORNING);
        }
        if (time.isBefore(MORNING)) {
            return LocalDateTime.of(at.toLocalDate(), MORNING);
        }
        return at;
    }

    private static LocalDateTime next9am(LocalDateTime now) {
        LocalDateTime today9 = LocalDateTime.of(now.toLocalDate(), LocalTime.of(9, 0));
        return now.isAfter(today9) ? now : today9;
    }

    private static String abbreviate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }

    static LocalDate today() {
        return LocalDate.now();
    }
}
