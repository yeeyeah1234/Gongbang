package com.gongbang.chat.service;

import com.gongbang.chat.domain.ChatMessage;
import com.gongbang.chat.domain.ChatRoomMember;
import com.gongbang.chat.dto.ChatMessageResponse;
import com.gongbang.chat.dto.ChatRoomListUpdateEvent;
import com.gongbang.chat.dto.ChatRoomResponse;
import com.gongbang.chat.repository.ChatMessageRepository;
import com.gongbang.chat.repository.ChatRoomMemberRepository;
import com.gongbang.member.dto.MemberProfileSummary;
import com.gongbang.member.service.MemberService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 채팅 저장·읽음 트랜잭션 커밋 후(`AFTER_COMMIT`) STOMP 메시지를 브로커로 전송한다. */
@Component
@RequiredArgsConstructor
public class ChatMessageAfterCommitListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final MemberService memberService;
    private final ChatMessageMapper chatMessageMapper;

    /** 새 메시지 저장이 커밋되면 채팅방 구독자와 참여자별 채팅방 목록 구독자에게 알린다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageSent(ChatMessageSentEvent event) {
        ChatMessage message = event.message();
        Map<Long, MemberProfileSummary> profiles =
                message.getSenderId() != null
                        ? memberService.getProfileSummaries(List.of(message.getSenderId()))
                        : Map.of();
        Map<Long, String> fileUrls =
                message.getFileId() != null
                        ? chatMessageMapper.resolveFileUrls(List.of(message.getFileId()))
                        : Map.of();

        ChatMessageResponse response =
                chatMessageMapper.toMessageResponse(message, profiles, fileUrls);
        messagingTemplate.convertAndSend(roomDestination(event.roomId()), response);

        ChatRoomResponse.LastMessageInfo lastMessageInfo =
                chatMessageMapper.toLastMessageInfo(message, profiles);
        LocalDateTime updatedAt =
                message.getCreatedAt() != null ? message.getCreatedAt() : LocalDateTime.now();

        for (ChatRoomMember crm : chatRoomMemberRepository.findAllByIdChatRoomId(event.roomId())) {
            Long participantId = crm.getId().getMemberId();
            long unreadCount = calculateUnreadCount(event.roomId(), message.getId(), crm);
            messagingTemplate.convertAndSend(
                    roomListDestination(event.teamId(), participantId),
                    new ChatRoomListUpdateEvent(
                            event.teamId(),
                            event.roomId(),
                            lastMessageInfo,
                            unreadCount,
                            updatedAt));
        }
    }

    /** 읽음 처리가 커밋되면 본인 채팅방 목록의 안읽음 수를 0(또는 잔여 수)으로 갱신한다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRoomRead(ChatRoomReadEvent event) {
        messagingTemplate.convertAndSend(
                roomListDestination(event.teamId(), event.memberId()),
                new ChatRoomListUpdateEvent(
                        event.teamId(),
                        event.roomId(),
                        event.lastMessage(),
                        0L,
                        event.updatedAt()));
    }

    static String roomDestination(Long roomId) {
        return "/sub/chat-rooms/" + roomId;
    }

    static String roomListDestination(Long teamId, Long memberId) {
        return "/sub/teams/" + teamId + "/members/" + memberId + "/chat-rooms";
    }

    private long calculateUnreadCount(Long roomId, Long latestMessageId, ChatRoomMember crm) {
        Long lastReadId = crm.getLastReadMessageId();
        if (lastReadId != null && lastReadId >= latestMessageId) {
            return 0L;
        }
        return lastReadId == null
                ? chatMessageRepository.countByChatRoomId(roomId)
                : chatMessageRepository.countByChatRoomIdAndIdGreaterThan(roomId, lastReadId);
    }
}
