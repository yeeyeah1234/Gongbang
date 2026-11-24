package com.gongbang.workspace.whiteboard.repository;

import java.time.LocalDateTime;

/** WB-01 목록 한 줄. 본문(content)은 읽지 않는다 (AGENTS.md 6). lastEditorId는 NULL일 수 있다. */
public record WhiteboardSummary(
        Long id, String title, Long creatorId, Long lastEditorId, LocalDateTime updatedAt) {}
