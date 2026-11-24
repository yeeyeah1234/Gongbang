package com.gongbang.workspace.notice.event;

import java.util.Objects;

/**
 * 공지 저장 트랜잭션의 AFTER_COMMIT에서 발행한다. memberId는 작성자의 회원 ID다.
 *
 * <p>알림 수신부는 일반 EventListener로 받고 REQUIRES_NEW 트랜잭션에서 저장하므로, 저장 트랜잭션 안에서 커밋 전에 직접 발행하면 안 된다.
 *
 * <p>NTC-06: title은 등록 시점의 제목, linkUrl은 해당 팀의 공지 탭에서 이 공지를 여는 프런트 주소다. API 주소나 팀 목록 주소가 아니라 공지 화면
 * 담당자가 정한 상세 주소를 전달한다.
 */
public record NoticeCreatedEvent(
        Long teamId, Long noticeId, Long memberId, String title, String linkUrl) {

    public NoticeCreatedEvent {
        Objects.requireNonNull(teamId, "teamId");
        Objects.requireNonNull(noticeId, "noticeId");
        Objects.requireNonNull(memberId, "memberId");
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("공지 제목이 필요합니다");
        }
        if (linkUrl == null || linkUrl.isBlank()) {
            throw new IllegalArgumentException("공지 상세 이동 주소가 필요합니다");
        }
    }
}
