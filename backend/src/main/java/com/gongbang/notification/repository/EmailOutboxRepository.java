package com.gongbang.notification.repository;

import com.gongbang.notification.domain.EmailOutbox;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, Long> {

    List<EmailOutbox> findTop100ByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
            EmailOutbox.Status status, LocalDateTime now);

    /** NOTI-06 ⑥ 하루 10통 = sent_at으로 센다 (보안 메일 제외) */
    long countByReceiverIdAndSecurityFalseAndSentAtAfter(Long receiverId, LocalDateTime after);
}
