package com.gongbang.chat.service;

import com.gongbang.chat.domain.ChatMessage;
import com.gongbang.chat.domain.ChatMessageType;
import com.gongbang.chat.dto.ChatMessageResponse;
import com.gongbang.chat.dto.ChatRoomResponse;
import com.gongbang.chat.dto.ChatSenderResponse;
import com.gongbang.member.dto.MemberProfileSummary;
import java.util.Collection;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** 채팅 메시지 엔티티를 REST·STOMP 응답 DTO로 조립하는 공용 매퍼. */
@Component
@RequiredArgsConstructor
class ChatMessageMapper {

    private final ObjectProvider<ChatFileUrlProvider> fileUrlProvider;

    ChatMessageResponse toMessageResponse(
            ChatMessage message,
            Map<Long, MemberProfileSummary> profiles,
            Map<Long, String> fileUrls) {
        ChatSenderResponse sender = toSenderResponse(message.getSenderId(), profiles);
        String imageUrl = message.getFileId() != null ? fileUrls.get(message.getFileId()) : null;
        return new ChatMessageResponse(
                message.getId(),
                message.getType(),
                sender,
                message.getContent(),
                imageUrl,
                message.getCreatedAt());
    }

    ChatRoomResponse.LastMessageInfo toLastMessageInfo(
            ChatMessage message, Map<Long, MemberProfileSummary> profiles) {
        if (message == null) {
            return null;
        }
        String senderNickname = null;
        if (message.getSenderId() != null) {
            MemberProfileSummary profile = profiles.get(message.getSenderId());
            senderNickname = profile != null ? profile.nickname() : "탈퇴한 사용자";
        }
        return new ChatRoomResponse.LastMessageInfo(
                toPreviewContent(message), senderNickname, message.getCreatedAt());
    }

    Map<Long, String> resolveFileUrls(Collection<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return Map.of();
        }
        ChatFileUrlProvider provider = fileUrlProvider.getIfAvailable();
        return provider != null ? provider.getFileUrls(fileIds) : Map.of();
    }

    static String toPreviewContent(ChatMessage message) {
        if (message.getType() == ChatMessageType.IMAGE
                && (message.getContent() == null || message.getContent().isBlank())) {
            return "사진";
        }
        return message.getContent();
    }

    private static ChatSenderResponse toSenderResponse(
            Long senderId, Map<Long, MemberProfileSummary> profiles) {
        if (senderId == null) {
            return null;
        }
        MemberProfileSummary profile = profiles.get(senderId);
        if (profile == null) {
            return new ChatSenderResponse(senderId, "탈퇴한 사용자", null);
        }
        return new ChatSenderResponse(senderId, profile.nickname(), profile.profileImageUrl());
    }
}
