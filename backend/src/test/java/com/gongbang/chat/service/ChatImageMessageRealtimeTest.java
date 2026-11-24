package com.gongbang.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.gongbang.chat.domain.ChatMessage;
import com.gongbang.chat.domain.ChatMessageType;
import com.gongbang.chat.domain.ChatRoom;
import com.gongbang.chat.domain.ChatRoomMember;
import com.gongbang.chat.domain.ChatRoomMemberId;
import com.gongbang.chat.domain.ChatRoomType;
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
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ChatImageMessageRealtimeTest {

    @Mock private ChatRoomRepository chatRoomRepository;
    @Mock private ChatRoomMemberRepository chatRoomMemberRepository;
    @Mock private ChatMessageRepository chatMessageRepository;
    @Mock private TeamMemberService teamMemberService;
    @Mock private TeamService teamService;
    @Mock private FileUploadService fileUploadService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private ChatRealtimeService service;

    @BeforeEach
    void setUp() {
        service =
                new ChatRealtimeService(
                        chatRoomRepository,
                        chatRoomMemberRepository,
                        chatMessageRepository,
                        teamMemberService,
                        teamService,
                        fileUploadService,
                        eventPublisher);
    }

    @Test
    @DisplayName("IMAGE 메시지 전송 시 본인 CHAT 파일 검증 후 fileId와 선택 캡션을 저장하고 이벤트를 발행한다")
    void 이미지_메시지_전송_성공() {
        ChatRoom room = ChatRoom.create(10L, ChatRoomType.TEAM, ChatRoom.teamRoomKey(10L));
        ReflectionTestUtils.setField(room, "id", 100L);
        ChatRoomMember senderMembership = ChatRoomMember.create(100L, 7L);

        given(chatRoomRepository.findById(100L)).willReturn(Optional.of(room));
        given(chatRoomMemberRepository.findById(new ChatRoomMemberId(100L, 7L)))
                .willReturn(Optional.of(senderMembership));
        given(chatMessageRepository.save(any(ChatMessage.class)))
                .willAnswer(
                        inv -> {
                            ChatMessage msg = inv.getArgument(0);
                            ReflectionTestUtils.setField(msg, "id", 88L);
                            ReflectionTestUtils.setField(
                                    msg, "createdAt", LocalDateTime.of(2026, 11, 24, 17, 30));
                            return msg;
                        });

        service.sendMessage(100L, 7L, new ChatSendRequest(ChatMessageType.IMAGE, "  ", 55L));

        verify(fileUploadService).requireOwnedChatFile(55L, 7L);
        ArgumentCaptor<ChatMessage> savedCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().getType()).isEqualTo(ChatMessageType.IMAGE);
        assertThat(savedCaptor.getValue().getFileId()).isEqualTo(55L);
        assertThat(savedCaptor.getValue().getContent()).isNull();
        verify(eventPublisher).publishEvent(any(ChatMessageSentEvent.class));
    }

    @Test
    @DisplayName("타인이 올렸거나 CHAT 용도가 아닌 fileId로 IMAGE 메시지 전송 시 저장하지 않는다")
    void 이미지_메시지_파일_검증_실패() {
        ChatRoom room = ChatRoom.create(10L, ChatRoomType.TEAM, ChatRoom.teamRoomKey(10L));
        ReflectionTestUtils.setField(room, "id", 100L);
        ChatRoomMember senderMembership = ChatRoomMember.create(100L, 7L);

        given(chatRoomRepository.findById(100L)).willReturn(Optional.of(room));
        given(chatRoomMemberRepository.findById(new ChatRoomMemberId(100L, 7L)))
                .willReturn(Optional.of(senderMembership));
        doThrow(new BusinessException(ErrorCode.NOT_OWNER))
                .when(fileUploadService)
                .requireOwnedChatFile(99L, 7L);

        assertThatThrownBy(
                        () ->
                                service.sendMessage(
                                        100L,
                                        7L,
                                        new ChatSendRequest(ChatMessageType.IMAGE, "캡션", 99L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_OWNER);
        verify(chatMessageRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }
}
