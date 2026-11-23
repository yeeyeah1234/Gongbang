package com.gongbang.workspace.document.dto;

/** DOC-03 이름 변경. 앞뒤 공백은 서버가 지우고, 비었으면 「제목 없는 문서」로 저장한다. */
public record DocumentRenameRequest(String title) {}
