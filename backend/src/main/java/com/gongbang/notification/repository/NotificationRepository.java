package com.gongbang.notification.repository;

import com.gongbang.notification.domain.Notification;
import com.gongbang.notification.domain.NotificationType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    long countByReceiverIdAndReadFalse(Long receiverId);

    @Query(
            "select n from Notification n where n.receiverId = :receiverId"
                    + " and (:unreadOnly = false or n.read = false)"
                    + " and n.type in :types"
                    + " order by n.createdAt desc, n.id desc")
    Page<Notification> findPage(
            @Param("receiverId") Long receiverId,
            @Param("unreadOnly") boolean unreadOnly,
            @Param("types") Collection<NotificationType> types,
            Pageable pageable);

    /** NOTI-04 ④ 같은 대상(글)의 안 읽은 댓글 알림 */
    Optional<Notification> findFirstByReceiverIdAndTypeAndTargetTypeAndTargetIdAndReadFalse(
            Long receiverId, NotificationType type, String targetType, Long targetId);

    @Modifying
    @Query(
            "update Notification n set n.read = true, n.readAt = :now"
                    + " where n.receiverId = :receiverId and n.read = false")
    int markAllRead(@Param("receiverId") Long receiverId, @Param("now") LocalDateTime now);

    @Modifying
    @Query("delete from Notification n where n.receiverId = :receiverId and n.read = true")
    int deleteRead(@Param("receiverId") Long receiverId);

    /** NOTI-01 ⑥ 30일 지난 알림 삭제 */
    @Modifying
    @Query("delete from Notification n where n.createdAt < :before")
    int deleteOlderThan(@Param("before") LocalDateTime before);

    /** NOTI-03 ⑤: 메일 링크로 들어와 화면을 열면 그 알림은 읽음 */
    @Modifying
    @Query(
            "update Notification n set n.read = true, n.readAt = :now"
                    + " where n.receiverId = :receiverId and n.linkUrl = :linkUrl and n.read = false")
    int markReadByLink(
            @Param("receiverId") Long receiverId,
            @Param("linkUrl") String linkUrl,
            @Param("now") LocalDateTime now);
}
