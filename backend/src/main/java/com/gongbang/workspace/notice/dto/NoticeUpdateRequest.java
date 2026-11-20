package com.gongbang.workspace.notice.dto;

/** NTC-04 수정. 제목·내용 둘 다 보낸다 (부분 수정 없음). 글자 수 규칙은 작성과 같고 service가 검사한다. */
public record NoticeUpdateRequest(String title, String content) {}
