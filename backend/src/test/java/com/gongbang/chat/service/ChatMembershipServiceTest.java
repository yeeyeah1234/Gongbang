package com.gongbang.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.gongbang.chat.domain.ChatRoom;
import com.gongbang.chat.domain.ChatRoomMember;
import com.gongbang.chat.domain.ChatRoomType;
import com.gongbang.chat.repository.ChatRoomMemberRepository;
import com.gongbang.chat.repository.ChatRoomRepository;
import com.gongbang.member.service.MemberService;
import com.gongbang.team.service.TeamService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ChatMembershipServiceTest {

    @Mock private ChatRoomRepository chatRoomRepository;
    @Mock private ChatRoomMemberRepository chatRoomMemberRepository;
    @Mock private TeamService teamService;
    @Mock private MemberService memberService;
    @Mock private ChatRealtimeService chatRealtimeService;

    private ChatMembershipService service;

    @BeforeEach
    void setUp() {
        service =
                new ChatMembershipService(
                        chatRoomRepository,
                        chatRoomMemberRepository,
                        teamService,
                        memberService,
                        chatRealtimeService);
    }

    @Test
    @DisplayName("팀 전체 채팅방 생성 시 팀장을 참여자로 등록한다")
    void 팀채팅방_생성() {
        given(chatRoomRepository.findByRoomKey("T:10")).willReturn(Optional.empty());
        given(chatRoomRepository.save(any(ChatRoom.class)))
                .willAnswer(
                        inv -> {
                            ChatRoom room = inv.getArgument(0);
                            ReflectionTestUtils.setField(room, "id", 100L);
                            return room;
                        });
        given(chatRoomMemberRepository.existsByIdChatRoomIdAndIdMemberId(100L, 1L))
                .willReturn(false);

        Long roomId = service.createTeamRoom(10L, 1L);

        assertThat(roomId).isEqualTo(100L);
        verify(chatRoomMemberRepository).save(any(ChatRoomMember.class));
    }

    @Test
    @DisplayName("새 팀원 추가 시 팀 채팅방에 넣고 입장 시스템 메시지를 남긴다")
    void 새팀원_입장_및_시스템메시지() {
        ChatRoom room = ChatRoom.create(10L, ChatRoomType.TEAM, "T:10");
        ReflectionTestUtils.setField(room, "id", 100L);
        given(chatRoomRepository.findByRoomKey("T:10")).willReturn(Optional.of(room));
        given(chatRoomMemberRepository.existsByIdChatRoomIdAndIdMemberId(100L, 2L))
                .willReturn(false);
        given(memberService.displayName(2L)).willReturn("민수");

        service.addMemberToTeamRoom(10L, 2L);

        verify(chatRoomMemberRepository).save(any(ChatRoomMember.class));
        verify(chatRealtimeService).sendSystemMessage(10L, "민수님이 입장했습니다.");
    }

    @Test
    @DisplayName("레거시 팀 동기화는 시스템 메시지 없이 누락된 팀원만 방에 채운다")
    void 레거시팀_초기화() {
        ChatRoom room = ChatRoom.create(10L, ChatRoomType.TEAM, "T:10");
        ReflectionTestUtils.setField(room, "id", 100L);
        given(chatRoomRepository.findByRoomKey("T:10")).willReturn(Optional.of(room));
        given(teamService.findActiveMemberIds(10L)).willReturn(List.of(1L, 2L));
        given(chatRoomMemberRepository.existsByIdChatRoomIdAndIdMemberId(100L, 1L))
                .willReturn(true);
        given(chatRoomMemberRepository.existsByIdChatRoomIdAndIdMemberId(100L, 2L))
                .willReturn(false);

        service.ensureTeamRoomsInitialized(10L);

        verify(chatRoomMemberRepository).save(any(ChatRoomMember.class));
        verify(chatRealtimeService, never()).sendSystemMessage(any(), any());
    }

    @Test
    @DisplayName("팀 채팅방 전체에서 나간 회원의 참여 행을 삭제한다")
    void 팀채팅방_참여자_일괄제거() {
        ChatRoom r1 = ChatRoom.create(10L, ChatRoomType.TEAM, "T:10");
        ChatRoom r2 = ChatRoom.create(10L, ChatRoomType.DIRECT, "D:10:1:2");
        ReflectionTestUtils.setField(r1, "id", 100L);
        ReflectionTestUtils.setField(r2, "id", 101L);
        given(chatRoomRepository.findAllByTeamId(10L)).willReturn(List.of(r1, r2));

        service.removeMemberFromTeamRooms(10L, 2L);

        verify(chatRoomMemberRepository)
                .deleteByIdChatRoomIdInAndIdMemberId(List.of(100L, 101L), 2L);
    }
}
