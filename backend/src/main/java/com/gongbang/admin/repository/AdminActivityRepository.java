package com.gongbang.admin.repository;

import com.gongbang.admin.domain.AdminActivity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminActivityRepository extends JpaRepository<AdminActivity, Long> {
    Page<AdminActivity> findByAction(String action, Pageable pageable);
}
