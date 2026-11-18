package com.gongbang.notification.service;

import com.gongbang.notification.domain.EmailOutbox;
import com.gongbang.notification.domain.SendMode;
import com.gongbang.notification.repository.EmailOutboxRepository;
import com.gongbang.notification.repository.NotificationRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** NOTI-06: email_outbox를 1분마다 보낸다 (Spring Mail SMTP) */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailDispatcher {

    /** ⑥ 한 사람 하루 최대 10통 (보안 메일 제외) */
    private static final int DAILY_LIMIT = 10;

    private final EmailOutboxRepository outboxRepository;
    private final NotificationRepository notificationRepository;
    private final ObjectProvider<JavaMailSender> mailSender;

    @Value("${spring.mail.host:}")
    private String mailHost;

    @Value("${spring.mail.port:587}")
    private int mailPort;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    @Value("${spring.mail.properties.mail.smtp.auth:true}")
    private boolean smtpAuth;

    // 보내는 사람. 비어 있으면 SMTP 로그인 계정(이메일 형식일 때)을 쓴다. Gmail·SendGrid 등은 인증된 주소만 보낼 수 있다
    @Value("${app.mail-from:}")
    private String from;

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void dispatchPending() {
        LocalDateTime now = LocalDateTime.now();
        for (EmailOutbox mail :
                outboxRepository.findTop100ByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
                        EmailOutbox.Status.PENDING, now)) {
            // ② 묶음 메일은 그 전에 사이트에서 읽었으면 뺀다
            if (mail.getSendMode() == SendMode.HOURLY_DIGEST && mail.getNotificationId() != null) {
                boolean read =
                        notificationRepository
                                .findById(mail.getNotificationId())
                                .map(n -> n.isRead())
                                .orElse(true);
                if (read) {
                    mail.cancel();
                    continue;
                }
            }
            if (!mail.isSecurity() && mail.getReceiverId() != null) {
                long sentToday =
                        outboxRepository.countByReceiverIdAndSecurityFalseAndSentAtAfter(
                                mail.getReceiverId(), LocalDate.now().atStartOfDay());
                if (sentToday >= DAILY_LIMIT) {
                    mail.postpone(
                            LocalDateTime.of(LocalDate.now().plusDays(1), LocalTime.of(8, 0)));
                    continue;
                }
            }
            dispatch(mail);
        }
    }

    /**
     * 서버가 켜질 때 메일 설정을 못 찾았으면 바로 알린다.
     *
     * <p>설정 위치: 테스트 서버는 systemd(gongbang-backend@blue/green)가 읽는 /srv/gongbang/deploy/backend.env의
     * MAIL_* 환경 변수, 내 컴퓨터는 backend/mail.properties. 메일 계정 주소는 로그에 남기지 않는다 (AGENTS 9).
     */
    @EventListener(ApplicationReadyEvent.class)
    public void warnIfMailNotConfigured() {
        if (isMailConfigured()) {
            log.info("메일 발송 준비됨: {}:{}", mailHost, mailPort);
        } else {
            log.warn(
                    "메일 설정을 찾지 못했어요 — 인증번호 메일이 안 갑니다. 서버는 deploy/backend.env의"
                            + " MAIL_HOST·MAIL_USERNAME·MAIL_PASSWORD, 내 컴퓨터는 backend/mail.properties 를"
                            + " 확인하세요 (지금 폴더: {})",
                    System.getProperty("user.dir"));
        }
    }

    /** 메일 서버(MAIL_HOST)가 있어 실제로 보낼 수 있는지 */
    public boolean isMailConfigured() {
        boolean hostSet = mailHost != null && !mailHost.isBlank();
        // 로그인이 필요한 SMTP(네이버·Gmail)인데 비밀번호를 아직 안 적었으면 설정 안 된 것으로 본다
        boolean loginReady = !smtpAuth || (mailPassword != null && !mailPassword.isBlank());
        return hostSet && loginReady && mailSender.getIfAvailable() != null;
    }

    /** 한 통 보내고 상태를 바꾼다. 성공하면 true */
    public boolean dispatch(EmailOutbox mail) {
        LocalDateTime now = LocalDateTime.now();
        JavaMailSender sender = mailSender.getIfAvailable();
        if (!isMailConfigured()) {
            // AUTH-05 ④: 메일을 보낼 수 없으면 보낸 척하지 않고 실패로 둔다 → 화면에 「메일을 보내지 못했어요」
            log.warn(
                    "메일 설정(MAIL_HOST) 없음 — 메일 id={} 종류={} 발송 실패", mail.getId(), mail.getMailType());
            mail.failed();
            return false;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(senderAddress());
            message.setTo(mail.getToEmail());
            message.setSubject(mail.getSubject());
            message.setText(mail.getBody());
            sender.send(message);
            mail.sent(now);
            return true;
        } catch (RuntimeException e) {
            // 원인 종류만 남긴다 (주소·내용은 남기지 않음, AGENTS 9). 인증 실패면 MailAuthenticationException
            log.warn(
                    "메일 발송 실패 id={} 종류={} 원인={}",
                    mail.getId(),
                    mail.getMailType(),
                    rootCauseName(e));
            mail.failed();
            return false;
        }
    }

    private String senderAddress() {
        if (from != null && !from.isBlank()) {
            return from;
        }
        if (mailUsername != null && mailUsername.contains("@")) {
            return "공방 <" + mailUsername + ">";
        }
        return "공방 <no-reply@gongbang.local>";
    }

    private static String rootCauseName(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return e.getClass().getSimpleName() + " / " + cause.getClass().getSimpleName();
    }
}
