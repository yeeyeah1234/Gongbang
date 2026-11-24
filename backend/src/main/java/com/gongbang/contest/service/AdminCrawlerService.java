package com.gongbang.contest.service;

import com.gongbang.contest.domain.CrawlerJob;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.service.AdminMemberService;
import jakarta.persistence.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminCrawlerService {
    private final EntityManager em;
    private final AdminMemberService access;

    public record Item(
            Long id,
            String status,
            java.time.LocalDateTime createdAt,
            java.time.LocalDateTime startedAt,
            java.time.LocalDateTime finishedAt) {
        static Item of(CrawlerJob j) {
            return new Item(
                    j.getId(),
                    j.getStatus(),
                    j.getCreatedAt(),
                    j.getStartedAt(),
                    j.getFinishedAt());
        }
    }

    @Transactional(readOnly = true)
    public List<Item> list(Long actor) {
        access.requireAdmin(actor);
        return em
                .createQuery("select j from CrawlerJob j order by j.id desc", CrawlerJob.class)
                .setMaxResults(20)
                .getResultList()
                .stream()
                .map(Item::of)
                .toList();
    }

    @Transactional
    public Item enqueue(Long actor) {
        access.requireAdmin(actor);
        var active =
                em.createQuery(
                                "select j from CrawlerJob j where j.status in ('PENDING','RUNNING')",
                                CrawlerJob.class)
                        .getResultList();
        if (!active.isEmpty()) return Item.of(active.get(0));
        var j = new CrawlerJob(actor);
        em.persist(j);
        em.flush();
        return Item.of(j);
    }

    @Transactional
    public Item claim() {
        var jobs =
                em.createQuery(
                                "select j from CrawlerJob j where j.status='PENDING' order by j.id",
                                CrawlerJob.class)
                        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                        .setMaxResults(1)
                        .getResultList();
        if (jobs.isEmpty()) return null;
        jobs.get(0).start();
        return Item.of(jobs.get(0));
    }

    @Transactional
    public void finish(Long id, boolean success) {
        var job = em.find(CrawlerJob.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (job == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        if (!"RUNNING".equals(job.getStatus()))
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        job.finish(success);
    }
}
