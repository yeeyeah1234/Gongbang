package com.gongbang.workspace.document.repository;

import java.time.LocalDateTime;

/** DOC-01 목록 한 줄. 본문(content)은 읽지 않는다 (AGENTS.md 6). */
public record DocumentSummary(
        Long id, String title, Long createdBy, Long updatedBy, LocalDateTime updatedAt) {}
