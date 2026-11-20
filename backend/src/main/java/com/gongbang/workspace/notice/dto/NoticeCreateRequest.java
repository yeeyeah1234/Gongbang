package com.gongbang.workspace.notice.dto;

/**
 * NTC-01 작성. 제목 1~50자, 내용 1~2000자 (앞뒤 공백을 뺀 뒤 센다). 값 검사는 service가 한다. pinned가 없으면(null) 고정하지 않는다.
 */
public record NoticeCreateRequest(String title, String content, Boolean pinned) {

    public boolean pinnedOrFalse() {
        return Boolean.TRUE.equals(pinned);
    }
}
