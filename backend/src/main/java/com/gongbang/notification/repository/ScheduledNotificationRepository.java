package com.gongbang.notification.repository;

import com.gongbang.notification.domain.ScheduledNotification;
import com.gongbang.notification.domain.ScheduledNotification.Status;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduledNotificationRepository
        extends JpaRepository<ScheduledNotification, Long> {

    List<ScheduledNotification> findByTargetTypeAndTargetIdAndStatus(
            String targetType, Long targetId, Status status);

    List<ScheduledNotification> findTop200ByStatusAndFireAtLessThanEqualOrderByFireAtAsc(
            Status status, LocalDateTime now);
}
