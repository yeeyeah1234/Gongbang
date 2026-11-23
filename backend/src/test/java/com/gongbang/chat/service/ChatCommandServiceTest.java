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
import com.gongbang.chat.dto.DirectChatCreateResponse;
import com.gongbang.chat.repository.ChatMessageRepository;
import com.gongbang.chat.repository.ChatRoomMemberRepository;
import com.gongbang.chat.repository.ChatRoomRepository;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.domain.Member;
import com.gongbang.member.dto.MemberProfileSummary;
import com.gongbang.member.service.MemberService;
import com.gongbang.team.service.TeamMemberService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ChatCommandServiceTest {

    @Mock private ChatRoomRepository chatRoomRepository;
    @Mock private ChatRoomMemberRepository chatRoomMemberRepository;
    @Mock private ChatMessageRepository chatMessageRepository;
    @Mock private TeamMemberService teamMemberService;
    @Mock private MemberService memberService;
    @Mock private ObjectProvider<ChatFileUrlProvider> fileUrlProvider;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private Member activeMember;

    private ChatCommandService service;

    @BeforeEach
    void setUp() {
        service =
                new ChatCommandService(
                        chatRoomRepository,
                        chatRoomMemberRepository,
                        chatMessageRepository,
                        teamMemberService,
                        memberService,
                        new ChatMessageMapper(fileUrlProvider),
                        eventPublisher);
    }

    @Test
    @DisplayName("자기 자신과는 1:1 채팅방을 열 수 없어 400 CANNOT_CHAT_SELF가 발생한다")
    void 자기자신_1대1_차단() {
        BusinessException ex =
                assertThrows(
                        BusinessException.class, () -> service.openOrCreateDirectRoom(10L, 1L, 1L));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CANNOT_CHAT_SELF);
    }

    @Test
    @DisplayName("1:1 채팅방이 없으면 정렬된 roomKey로 새로 만들고 두 사람을 참여자로 추가한다")
    void 일대일방_신규생성() {
        given(memberService.findActiveMember(2L)).willReturn(Optional.of(activeMember));
        given(chatRoomRepository.findByRoomKey("D:10:2:5")).willReturn(Optional.empty());
        given(chatRoomRepository.save(any(ChatRoom.class)))
                .willAnswer(
                        inv -> {
                            ChatRoom r = inv.getArgument(0);
                            ReflectionTestUtils.setField(r, "id", 300L);
                            return r;
                        });

        DirectChatCreateResponse response = service.openOrCreateDirectRoom(10L, 5L, 2L);

        assertThat(response.roomId()).isEqualTo(300L);
        verify(teamMemberService).requireMember(10L, 5L);
        verify(teamMemberService).requireMember(10L, 2L);
    }

    @Test
    @DisplayName("읽음 처리 시 단조 증가 형태로 갱신하고 읽음 이벤트를 발행한다")
    void 읽음_처리() {
        ChatRoom room = ChatRoom.create(10L, ChatRoomType.TEAM, "T:10");
        ReflectionTestUtils.setField(room, "id", 100L);
        ChatRoomMember crm = ChatRoomMember.create(100L, 1L);
        crm.updateLastReadMessageId(5L);
        ChatMessage latest = ChatMessage.create(100L, 2L, ChatMessageType.TEXT, "최근", null);
        ReflectionTestUtils.setField(latest, "id", 12L);
        ReflectionTestUtils.setField(latest, "createdAt", LocalDateTime.of(2026, 11, 20, 16, 0));

        given(chatRoomRepository.findById(100L)).willReturn(Optional.of(room));
        given(chatRoomMemberRepository.findById(new ChatRoomMemberId(100L, 1L)))
                .willReturn(Optional.of(crm));
        given(chatMessageRepository.existsByIdAndChatRoomId(12L, 100L)).willReturn(true);
        given(chatMessageRepository.findLatestMessagesByRoomIds(List.of(100L)))
                .willReturn(List.of(latest));
        given(memberService.getProfileSummaries(List.of(2L)))
                .willReturn(Map.of(2L, new MemberProfileSummary(2L, "상현", null, List.of())));

        service.markAsRead(100L, 1L, 12L);

        assertThat(crm.getLastReadMessageId()).isEqualTo(12L);
        verify(eventPublisher).publishEvent(any(ChatRoomReadEvent.class));
    }
}
