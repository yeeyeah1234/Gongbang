package com.gongbang.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.gongbang.chat.domain.ChatMessage;
import com.gongbang.chat.domain.ChatMessageType;
import com.gongbang.chat.domain.ChatRoom;
import com.gongbang.chat.domain.ChatRoomMember;
import com.gongbang.chat.domain.ChatRoomMemberId;
import com.gongbang.chat.domain.ChatRoomType;
import com.gongbang.chat.dto.ChatMessagePageResponse;
import com.gongbang.chat.dto.ChatRoomResponse;
import com.gongbang.chat.repository.ChatMessageRepository;
import com.gongbang.chat.repository.ChatRoomMemberRepository;
import com.gongbang.chat.repository.ChatRoomRepository;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.dto.MemberProfileSummary;
import com.gongbang.member.service.MemberService;
import com.gongbang.team.service.TeamMemberService;
import com.gongbang.team.service.TeamService;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ChatQueryServiceTest {

    @Mock private ChatRoomRepository chatRoomRepository;
    @Mock private ChatRoomMemberRepository chatRoomMemberRepository;
    @Mock private ChatMessageRepository chatMessageRepository;
    @Mock private TeamMemberService teamMemberService;
    @Mock private TeamService teamService;
    @Mock private MemberService memberService;
    @Mock private ChatMembershipService chatMembershipService;
    @Mock private ObjectProvider<ChatFileUrlProvider> fileUrlProvider;

    private ChatQueryService service;

    @BeforeEach
    void setUp() {
        service =
                new ChatQueryService(
                        chatRoomRepository,
                        chatRoomMemberRepository,
                        chatMessageRepository,
                        teamMemberService,
                        teamService,
                        memberService,
                        chatMembershipService,
                        new ChatMessageMapper(fileUrlProvider));
    }

    @Test
    @DisplayName("채팅방 목록은 TEAM 이름·DIRECT 상대방·이미지 요약·안읽음 수를 포함해 반환한다")
    void 채팅방_목록_조회() {
        ChatRoom teamRoom = ChatRoom.create(10L, ChatRoomType.TEAM, "T:10");
        ReflectionTestUtils.setField(teamRoom, "id", 100L);
        ChatRoom directRoom = ChatRoom.create(10L, ChatRoomType.DIRECT, "D:10:1:2");
        ReflectionTestUtils.setField(directRoom, "id", 101L);

        ChatRoomMember myTeamMem = ChatRoomMember.create(100L, 1L);
        myTeamMem.updateLastReadMessageId(10L);
        ChatRoomMember myDirectMem = ChatRoomMember.create(101L, 1L);
        ChatRoomMember partnerDirectMem = ChatRoomMember.create(101L, 2L);

        ChatMessage teamMsg = ChatMessage.create(100L, 2L, ChatMessageType.IMAGE, null, 77L);
        ReflectionTestUtils.setField(teamMsg, "id", 12L);
        ReflectionTestUtils.setField(teamMsg, "createdAt", LocalDateTime.of(2026, 11, 20, 15, 0));

        given(chatRoomRepository.findJoinedRoomsOrderByRecent(10L, 1L))
                .willReturn(List.of(teamRoom, directRoom));
        given(chatRoomMemberRepository.findAllByIdChatRoomIdIn(List.of(100L, 101L)))
                .willReturn(List.of(myTeamMem, myDirectMem, partnerDirectMem));
        given(chatMessageRepository.findLatestMessagesByRoomIds(List.of(100L, 101L)))
                .willReturn(List.of(teamMsg));
        given(chatMessageRepository.countByChatRoomIdAndIdGreaterThan(100L, 10L)).willReturn(2L);
        given(memberService.getProfileSummaries(anyCollection()))
                .willReturn(
                        Map.of(2L, new MemberProfileSummary(2L, "상현", "/img/2.png", List.of())));
        given(teamService.getTeamName(10L)).willReturn("공방팀");

        List<ChatRoomResponse> result = service.getChatRooms(10L, 1L);

        verify(teamMemberService).requireMember(10L, 1L);
        verify(chatMembershipService).ensureTeamRoomsInitialized(10L);
        assertThat(result).hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("공방팀");
        assertThat(result.get(0).lastMessage().content()).isEqualTo("사진");
        assertThat(result.get(0).unreadCount()).isEqualTo(2L);
        assertThat(result.get(1).name()).isEqualTo("상현");
        assertThat(result.get(1).partner().memberId()).isEqualTo(2L);
        assertThat(result.get(1).unreadCount()).isZero();
    }

    @Test
    @DisplayName("메시지 커서 조회는 DESC로 size+1 조회 후 오래된 순(ASC)과 hasNext를 반환한다")
    void 메시지_커서_페이징_조회() {
        ChatRoom room = ChatRoom.create(10L, ChatRoomType.TEAM, "T:10");
        ReflectionTestUtils.setField(room, "id", 100L);
        given(chatRoomRepository.findById(100L)).willReturn(Optional.of(room));
        given(chatRoomMemberRepository.existsById(new ChatRoomMemberId(100L, 1L))).willReturn(true);

        ChatMessage m3 = ChatMessage.create(100L, 1L, ChatMessageType.TEXT, "셋", null);
        ReflectionTestUtils.setField(m3, "id", 3L);
        ChatMessage m2 = ChatMessage.system(100L, "둘");
        ReflectionTestUtils.setField(m2, "id", 2L);
        ChatMessage m1 = ChatMessage.create(100L, 1L, ChatMessageType.TEXT, "하나", null);
        ReflectionTestUtils.setField(m1, "id", 1L);

        given(chatMessageRepository.findByChatRoomIdOrderByIdDesc(100L, PageRequest.of(0, 3)))
                .willReturn(List.of(m3, m2, m1));
        given(memberService.getProfileSummaries(anyCollection()))
                .willReturn(Map.of(1L, new MemberProfileSummary(1L, "건우", null, List.of())));

        ChatMessagePageResponse page = service.getMessages(100L, 1L, null, 2);

        assertThat(page.hasNext()).isTrue();
        assertThat(page.items()).extracting(m -> m.messageId()).containsExactly(2L, 3L);
        assertThat(page.items().get(0).sender()).isNull();
        assertThat(page.items().get(1).sender().nickname()).isEqualTo("건우");
    }

    @Test
    @DisplayName("채팅방 참여자가 아니면 메시지 조회 시 403 NOT_CHAT_ROOM_MEMBER가 발생한다")
    void 비참여자_메시지_조회_차단() {
        ChatRoom room = ChatRoom.create(10L, ChatRoomType.DIRECT, "D:10:1:2");
        ReflectionTestUtils.setField(room, "id", 100L);
        given(chatRoomRepository.findById(100L)).willReturn(Optional.of(room));
        given(chatRoomMemberRepository.existsById(new ChatRoomMemberId(100L, 3L)))
                .willReturn(false);

        BusinessException ex =
                assertThrows(
                        BusinessException.class, () -> service.getMessages(100L, 3L, null, 30));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.NOT_CHAT_ROOM_MEMBER);
    }

    @Test
    @DisplayName("메시지가 있는 방을 한 번도 읽지 않아 lastReadMessageId가 null이어도 NPE 없이 전체 개수를 안읽음 수로 반환한다")
    void 미열람_채팅방_안읽음수_조회() {
        ChatRoom teamRoom = ChatRoom.create(10L, ChatRoomType.TEAM, "T:10");
        ReflectionTestUtils.setField(teamRoom, "id", 100L);
        ChatRoomMember unreadMember = ChatRoomMember.create(100L, 1L);
        ChatMessage systemMsg = ChatMessage.system(100L, "팀원이 입장했습니다.");
        ReflectionTestUtils.setField(systemMsg, "id", 5L);
        ReflectionTestUtils.setField(systemMsg, "createdAt", LocalDateTime.of(2026, 11, 23, 21, 0));

        given(chatRoomRepository.findJoinedRoomsOrderByRecent(10L, 1L))
                .willReturn(List.of(teamRoom));
        given(chatRoomMemberRepository.findAllByIdChatRoomIdIn(List.of(100L)))
                .willReturn(List.of(unreadMember));
        given(chatMessageRepository.findLatestMessagesByRoomIds(List.of(100L)))
                .willReturn(List.of(systemMsg));
        given(chatMessageRepository.countByChatRoomId(100L)).willReturn(3L);
        given(teamService.getTeamName(10L)).willReturn("김건우 구함");

        List<ChatRoomResponse> result = service.getChatRooms(10L, 1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).unreadCount()).isEqualTo(3L);
        assertThat(result.get(0).lastMessage().content()).isEqualTo("팀원이 입장했습니다.");
    }
}
