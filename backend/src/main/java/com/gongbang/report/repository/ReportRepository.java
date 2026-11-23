package com.gongbang.report.repository;

import com.gongbang.report.domain.Report;
import org.springframework.data.jpa.repository.JpaRepository;

/** 신고 JPA 레포지토리. */
public interface ReportRepository extends JpaRepository<Report, Long> {}
