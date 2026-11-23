package com.gongbang.chat.controller;

import com.gongbang.chat.dto.ChatSendRequest;
import com.gongbang.chat.service.ChatRealtimeService;
import com.gongbang.global.websocket.StompPrincipal;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

/** NET-05 STOMP 실시간 채팅 메시지 수신 컨트롤러 (`/pub/chat-rooms/{roomId}/send`). */
@Controller
@RequiredArgsConstructor
public class ChatStompController {

    private final ChatRealtimeService chatRealtimeService;

    /** 채팅방 메시지 전송 STOMP 핸들러. */
    @MessageMapping("/chat-rooms/{roomId}/send")
    public void sendMessage(
            @DestinationVariable Long roomId,
            @Payload ChatSendRequest request,
            Principal principal) {
        Long senderId = extractMemberId(principal);
        chatRealtimeService.sendMessage(roomId, senderId, request);
    }

    private static Long extractMemberId(Principal principal) {
        if (principal instanceof StompPrincipal stompPrincipal) {
            return stompPrincipal.memberId();
        }
        if (principal == null || principal.getName() == null) {
            throw new MessageDeliveryException("인증된 사용자 정보가 없습니다.");
        }
        return Long.valueOf(principal.getName());
    }
}
