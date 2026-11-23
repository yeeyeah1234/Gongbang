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

/** 채팅 메시지 엔티티 (`chat_message`). */
@Entity
@Table(name = "chat_message")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_room_id", nullable = false)
    private Long chatRoomId;

    @Column(name = "sender_id")
    private Long senderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10)
    private ChatMessageType type;

    @Column(name = "content", length = 2000)
    private String content;

    @Column(name = "file_id")
    private Long fileId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private ChatMessage(
            Long chatRoomId, Long senderId, ChatMessageType type, String content, Long fileId) {
        this.chatRoomId = chatRoomId;
        this.senderId = senderId;
        this.type = type;
        this.content = content;
        this.fileId = fileId;
    }

    /** 일반 텍스트·이미지 또는 시스템 채팅 메시지를 생성한다. */
    public static ChatMessage create(
            Long chatRoomId, Long senderId, ChatMessageType type, String content, Long fileId) {
        return new ChatMessage(chatRoomId, senderId, type, content, fileId);
    }

    /** 시스템 메시지를 생성한다 (`sender_id = NULL`). */
    public static ChatMessage system(Long chatRoomId, String content) {
        return new ChatMessage(chatRoomId, null, ChatMessageType.SYSTEM, content, null);
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
