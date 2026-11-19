package com.gongbang.workspace.sync;

/** WS-06: 동기화 서버 POST /internal/disconnect 요청 body */
public record SyncDisconnectRequest(Long teamId, Long memberId) {}
