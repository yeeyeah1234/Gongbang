package com.gongbang.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.gongbang.chat.domain.ChatMessage;
import com.gongbang.chat.domain.ChatMessageType;
import com.gongbang.chat.domain.ChatRoomMember;
import com.gongbang.chat.dto.ChatMessageResponse;
import com.gongbang.chat.dto.ChatRoomListUpdateEvent;
import com.gongbang.chat.repository.ChatMessageRepository;
import com.gongbang.chat.repository.ChatRoomMemberRepository;
import com.gongbang.member.dto.MemberProfileSummary;
import com.gongbang.member.service.MemberService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ChatMessageAfterCommitListenerTest {

    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private ChatRoomMemberRepository chatRoomMemberRepository;
    @Mock private ChatMessageRepository chatMessageRepository;
    @Mock private MemberService memberService;
    @Mock private ObjectProvider<ChatFileUrlProvider> fileUrlProvider;

    private ChatMessageAfterCommitListener listener;

    @BeforeEach
    void setUp() {
        listener =
                new ChatMessageAfterCommitListener(
                        messagingTemplate,
                        chatRoomMemberRepository,
                        chatMessageRepository,
                        memberService,
                        new ChatMessageMapper(fileUrlProvider));
    }

    @Test
    @DisplayName("메시지 커밋 후 방 구독자에게 메시지를 브로드캐스트하고 참여자별 목록·안읽음 수를 갱신한다")
    void 메시지_커밋_후_브로드캐스트() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 20, 14, 0);
        ChatMessage message = ChatMessage.create(100L, 1L, ChatMessageType.TEXT, "안녕", null);
        ReflectionTestUtils.setField(message, "id", 50L);
        ReflectionTestUtils.setField(message, "createdAt", now);

        ChatRoomMember sender = ChatRoomMember.create(100L, 1L);
        sender.updateLastReadMessageId(50L);
        ChatRoomMember receiver = ChatRoomMember.create(100L, 2L);
        receiver.updateLastReadMessageId(47L);

        given(memberService.getProfileSummaries(List.of(1L)))
                .willReturn(
                        Map.of(1L, new MemberProfileSummary(1L, "건우", "/img/1.png", List.of())));
        given(chatRoomMemberRepository.findAllByIdChatRoomId(100L))
                .willReturn(List.of(sender, receiver));
        given(chatMessageRepository.countByChatRoomIdAndIdGreaterThan(100L, 47L)).willReturn(3L);

        listener.onMessageSent(new ChatMessageSentEvent(10L, 100L, message));

        ArgumentCaptor<ChatMessageResponse> msgCaptor =
                ArgumentCaptor.forClass(ChatMessageResponse.class);
        verify(messagingTemplate).convertAndSend(eq("/sub/chat-rooms/100"), msgCaptor.capture());
        assertThat(msgCaptor.getValue().messageId()).isEqualTo(50L);
        assertThat(msgCaptor.getValue().sender().nickname()).isEqualTo("건우");

        ArgumentCaptor<ChatRoomListUpdateEvent> senderListCaptor =
                ArgumentCaptor.forClass(ChatRoomListUpdateEvent.class);
        verify(messagingTemplate)
                .convertAndSend(
                        eq("/sub/teams/10/members/1/chat-rooms"), senderListCaptor.capture());
        assertThat(senderListCaptor.getValue().unreadCount()).isZero();

        ArgumentCaptor<ChatRoomListUpdateEvent> receiverListCaptor =
                ArgumentCaptor.forClass(ChatRoomListUpdateEvent.class);
        verify(messagingTemplate)
                .convertAndSend(
                        eq("/sub/teams/10/members/2/chat-rooms"), receiverListCaptor.capture());
        assertThat(receiverListCaptor.getValue().unreadCount()).isEqualTo(3L);
    }
}
