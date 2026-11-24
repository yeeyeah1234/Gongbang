package com.gongbang.workspace.whiteboard.dto;

/** WB-01 제목 변경. 앞뒤 공백은 서버가 지우고, 비었으면 「제목 없는 보드」로 저장한다. */
public record WhiteboardRenameRequest(String title) {}
