package com.gongbang.chat.service;

import com.gongbang.chat.domain.ChatMessage;
import com.gongbang.chat.domain.ChatMessageType;
import com.gongbang.chat.domain.ChatRoom;
import com.gongbang.chat.domain.ChatRoomMember;
import com.gongbang.chat.domain.ChatRoomMemberId;
import com.gongbang.chat.dto.ChatSendRequest;
import com.gongbang.chat.repository.ChatMessageRepository;
import com.gongbang.chat.repository.ChatRoomMemberRepository;
import com.gongbang.chat.repository.ChatRoomRepository;
import com.gongbang.file.service.FileUploadService;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.team.service.TeamMemberService;
import com.gongbang.team.service.TeamService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 채팅 메시지 실시간 저장 및 STOMP 발송 트리거 서비스. */
@Service
@RequiredArgsConstructor
public class ChatRealtimeService {

    private static final int MAX_CONTENT_LENGTH = 2000;

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final TeamMemberService teamMemberService;
    private final TeamService teamService;
    private final FileUploadService fileUploadService;
    private final ApplicationEventPublisher eventPublisher;

    /** STOMP `/pub/chat-rooms/{roomId}/send` 요청을 검증·저장하고 커밋 후 브로드캐스트 이벤트를 발행한다. */
    @Transactional
    public void sendMessage(Long roomId, Long senderId, ChatSendRequest request) {
        ChatRoom room =
                chatRoomRepository
                        .findById(roomId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        teamMemberService.requireMember(room.getTeamId(), senderId);
        ChatRoomMember senderMembership =
                chatRoomMemberRepository
                        .findById(new ChatRoomMemberId(roomId, senderId))
                        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_CHAT_ROOM_MEMBER));

        validateSendRequest(request);
        Long validatedFileId = null;
        if (request.type() == ChatMessageType.IMAGE) {
            fileUploadService.requireOwnedChatFile(request.fileId(), senderId);
            validatedFileId = request.fileId();
        }

        ChatMessage saved =
                chatMessageRepository.save(
                        ChatMessage.create(
                                roomId,
                                senderId,
                                request.type(),
                                normalizeContent(request.content()),
                                validatedFileId));

        LocalDateTime sentAt =
                saved.getCreatedAt() != null ? saved.getCreatedAt() : LocalDateTime.now();
        room.touchLastMessage(sentAt);
        teamService.touchLastActivity(room.getTeamId(), sentAt);
        senderMembership.updateLastReadMessageId(saved.getId());

        eventPublisher.publishEvent(new ChatMessageSentEvent(room.getTeamId(), roomId, saved));
    }

    /** 팀 전체 채팅방에 `SYSTEM` 메시지를 저장하고 커밋 후 브로드캐스트 이벤트를 발행한다. */
    @Transactional
    public void sendSystemMessage(Long teamId, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        chatRoomRepository
                .findByRoomKey(ChatRoom.teamRoomKey(teamId))
                .ifPresent(
                        room -> {
                            ChatMessage saved =
                                    chatMessageRepository.save(
                                            ChatMessage.system(room.getId(), content));
                            LocalDateTime sentAt =
                                    saved.getCreatedAt() != null
                                            ? saved.getCreatedAt()
                                            : LocalDateTime.now();
                            room.touchLastMessage(sentAt);
                            teamService.touchLastActivity(teamId, sentAt);
                            eventPublisher.publishEvent(
                                    new ChatMessageSentEvent(teamId, room.getId(), saved));
                        });
    }

    private static void validateSendRequest(ChatSendRequest request) {
        if (request == null || request.type() == null || request.type() == ChatMessageType.SYSTEM) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "올바른 메시지 형식이 아닙니다.");
        }
        if (request.type() == ChatMessageType.TEXT) {
            if (request.content() == null || request.content().isBlank()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "메시지 내용을 입력해 주세요.");
            }
            if (request.content().length() > MAX_CONTENT_LENGTH) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "메시지는 2000자까지 보낼 수 있어요.");
            }
        } else if (request.type() == ChatMessageType.IMAGE) {
            if (request.fileId() == null) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "이미지 파일 정보가 없습니다.");
            }
            if (request.content() != null && request.content().length() > MAX_CONTENT_LENGTH) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "메시지는 2000자까지 보낼 수 있어요.");
            }
        }
    }

    private static String normalizeContent(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        return content;
    }
}
