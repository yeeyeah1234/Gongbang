package com.gongbang.workspace.whiteboard.dto;

/** WB-01 만들기·제목 변경 응답 (API 명세서: { whiteboardId, title }). */
public record WhiteboardTitleResponse(Long whiteboardId, String title) {}
