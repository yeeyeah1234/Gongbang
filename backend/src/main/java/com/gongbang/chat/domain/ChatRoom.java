package com.gongbang.chat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 채팅방 엔티티 (`chat_room`). */
@Entity
@Table(name = "chat_room")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10)
    private ChatRoomType type;

    @Column(name = "room_key", nullable = false, unique = true, length = 60)
    private String roomKey;

    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private ChatRoom(Long teamId, ChatRoomType type, String roomKey) {
        this.teamId = teamId;
        this.type = type;
        this.roomKey = roomKey;
    }

    /** 팀 전체 채팅방 키(`T:{teamId}`)를 생성한다. */
    public static String teamRoomKey(Long teamId) {
        return "T:" + teamId;
    }

    /** 1:1 채팅방 키(`D:{teamId}:{작은ID}:{큰ID}`)를 생성한다. */
    public static String directRoomKey(Long teamId, Long memberA, Long memberB) {
        long min = Math.min(memberA, memberB);
        long max = Math.max(memberA, memberB);
        return "D:" + teamId + ":" + min + ":" + max;
    }

    /** 새 채팅방을 생성한다. */
    public static ChatRoom create(Long teamId, ChatRoomType type, String roomKey) {
        return new ChatRoom(teamId, type, roomKey);
    }

    /** 마지막 메시지 전송 시각을 갱신한다. */
    public void touchLastMessage(LocalDateTime sentAt) {
        this.lastMessageAt = sentAt != null ? sentAt : LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
