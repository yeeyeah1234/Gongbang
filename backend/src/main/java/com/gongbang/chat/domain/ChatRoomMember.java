package com.gongbang.chat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 채팅방 참여자 및 마지막 읽은 메시지 엔티티 (`chat_room_member`). */
@Entity
@Table(name = "chat_room_member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoomMember {

    @EmbeddedId private ChatRoomMemberId id;

    @Column(name = "last_read_message_id")
    private Long lastReadMessageId;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private LocalDateTime joinedAt;

    private ChatRoomMember(ChatRoomMemberId id) {
        this.id = id;
    }

    /** 채팅방 참여자를 생성한다. */
    public static ChatRoomMember create(Long chatRoomId, Long memberId) {
        return new ChatRoomMember(new ChatRoomMemberId(chatRoomId, memberId));
    }

    /** 마지막으로 읽은 메시지 ID를 단조 증가 형태로 갱신한다. */
    public void updateLastReadMessageId(Long messageId) {
        if (messageId != null
                && (this.lastReadMessageId == null || messageId > this.lastReadMessageId)) {
            this.lastReadMessageId = messageId;
        }
    }

    @PrePersist
    protected void onCreate() {
        if (this.joinedAt == null) {
            this.joinedAt = LocalDateTime.now();
        }
    }
}
