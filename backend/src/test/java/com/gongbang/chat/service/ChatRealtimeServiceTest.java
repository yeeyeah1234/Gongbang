package com.gongbang.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
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
class ChatRealtimeServiceTest {

    @Mock private ChatRoomRepository chatRoomRepository;
    @Mock private ChatRoomMemberRepository chatRoomMemberRepository;
    @Mock private ChatMessageRepository chatMessageRepository;
    @Mock private TeamMemberService teamMemberService;
    @Mock private TeamService teamService;
    @Mock private com.gongbang.file.service.FileUploadService fileUploadService;
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
    @DisplayName("텍스트 메시지 전송 시 저장 후 방·팀 최근 활동 시각과 보낸 사람 읽음 ID를 갱신하고 이벤트를 발행한다")
    void 텍스트_전송_성공() {
        ChatRoom room = ChatRoom.create(10L, ChatRoomType.TEAM, "T:10");
        ReflectionTestUtils.setField(room, "id", 100L);
        ChatRoomMember sender = ChatRoomMember.create(100L, 1L);

        given(chatRoomRepository.findById(100L)).willReturn(Optional.of(room));
        given(chatRoomMemberRepository.findById(new ChatRoomMemberId(100L, 1L)))
                .willReturn(Optional.of(sender));
        given(chatMessageRepository.save(any(ChatMessage.class)))
                .willAnswer(
                        inv -> {
                            ChatMessage msg = inv.getArgument(0);
                            ReflectionTestUtils.setField(msg, "id", 555L);
                            ReflectionTestUtils.setField(
                                    msg, "createdAt", LocalDateTime.of(2026, 11, 20, 12, 0));
                            return msg;
                        });

        service.sendMessage(100L, 1L, new ChatSendRequest(ChatMessageType.TEXT, "안녕하세요", null));

        assertThat(room.getLastMessageAt()).isEqualTo(LocalDateTime.of(2026, 11, 20, 12, 0));
        assertThat(sender.getLastReadMessageId()).isEqualTo(555L);
        verify(teamService).touchLastActivity(10L, LocalDateTime.of(2026, 11, 20, 12, 0));

        ArgumentCaptor<ChatMessageSentEvent> eventCaptor =
                ArgumentCaptor.forClass(ChatMessageSentEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().message().getId()).isEqualTo(555L);
    }

    @Test
    @DisplayName("SYSTEM 형식이나 빈 텍스트·fileId 없는 이미지 요청은 INVALID_INPUT 예외가 발생한다")
    void 메시지_형식_검증() {
        ChatRoom room = ChatRoom.create(10L, ChatRoomType.TEAM, "T:10");
        ReflectionTestUtils.setField(room, "id", 100L);
        given(chatRoomRepository.findById(100L)).willReturn(Optional.of(room));
        given(chatRoomMemberRepository.findById(new ChatRoomMemberId(100L, 1L)))
                .willReturn(Optional.of(ChatRoomMember.create(100L, 1L)));

        BusinessException systemEx =
                assertThrows(
                        BusinessException.class,
                        () ->
                                service.sendMessage(
                                        100L,
                                        1L,
                                        new ChatSendRequest(ChatMessageType.SYSTEM, "hi", null)));
        assertThat(systemEx.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT);

        BusinessException emptyTextEx =
                assertThrows(
                        BusinessException.class,
                        () ->
                                service.sendMessage(
                                        100L,
                                        1L,
                                        new ChatSendRequest(ChatMessageType.TEXT, "   ", null)));
        assertThat(emptyTextEx.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT);

        BusinessException noFileEx =
                assertThrows(
                        BusinessException.class,
                        () ->
                                service.sendMessage(
                                        100L,
                                        1L,
                                        new ChatSendRequest(ChatMessageType.IMAGE, null, null)));
        assertThat(noFileEx.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
    }
}
