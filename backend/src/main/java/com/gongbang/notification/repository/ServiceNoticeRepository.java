package com.gongbang.notification.repository;

import com.gongbang.notification.domain.ServiceNotice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceNoticeRepository extends JpaRepository<ServiceNotice, Long> {
    @org.springframework.data.jpa.repository.Lock(
            jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select n from ServiceNotice n where n.id=:id")
    java.util.Optional<ServiceNotice> findForUpdate(
            @org.springframework.data.repository.query.Param("id") Long id);
}
