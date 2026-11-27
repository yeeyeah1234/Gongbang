package com.gongbang.admin.service;

import com.gongbang.admin.domain.AdminActivity;
import com.gongbang.admin.repository.AdminActivityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminActivityWriter {
    private final AdminActivityRepository repository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Long actor, String action, String type, Long target, String reason) {
        repository.save(new AdminActivity(actor, action, type, target, reason));
    }
}
