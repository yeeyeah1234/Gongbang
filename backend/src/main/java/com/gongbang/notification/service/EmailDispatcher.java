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

    @Value("${app.mail-from:공방 <no-reply@gongbang.local>}")
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

    /** 한 통 보내고 상태를 바꾼다. 성공하면 true */
    public boolean dispatch(EmailOutbox mail) {
        LocalDateTime now = LocalDateTime.now();
        JavaMailSender sender = mailSender.getIfAvailable();
        if (mailHost == null || mailHost.isBlank() || sender == null) {
            // 로컬 개발: SMTP가 없으면 보내지 않고 보낸 것으로 친다 (메일 내용·주소는 로그에 남기지 않음, AGENTS 9)
            log.info("MAIL_HOST 없음 — 메일 id={} 종류={} 발송 생략", mail.getId(), mail.getMailType());
            mail.sent(now);
            return true;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(mail.getToEmail());
            message.setSubject(mail.getSubject());
            message.setText(mail.getBody());
            sender.send(message);
            mail.sent(now);
            return true;
        } catch (RuntimeException e) {
            log.warn("메일 발송 실패 id={} 종류={}", mail.getId(), mail.getMailType());
            mail.failed();
            return false;
        }
    }
}
