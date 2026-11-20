package com.gongbang.workspace.notice.domain;

/**
 * NTC-06: 공지가 바뀔 때마다(작성·수정·삭제·고정·해제) 발행한다. 화면 갱신 신호(STOMP, 김건우)가 받을 예정이다.
 *
 * <p>고정 공지가 바뀌면 새로 고정된 공지에 PINNED, 밀려난 공지에 UNPINNED를 따로 발행한다. 이미 그 상태라 바뀐 것이 없으면 발행하지 않는다. 받는 쪽은
 * {@link NoticeCreatedEvent}와 같이 AFTER_COMMIT으로 받는다.
 */
public record NoticeChangedEvent(Long teamId, Long noticeId, NoticeChangeType type) {}
