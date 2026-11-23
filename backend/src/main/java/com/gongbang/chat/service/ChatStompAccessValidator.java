package com.gongbang.chat.service;

import com.gongbang.chat.domain.ChatRoom;
import com.gongbang.chat.repository.ChatRoomMemberRepository;
import com.gongbang.chat.repository.ChatRoomRepository;
import com.gongbang.global.websocket.ChatRoomAccessChecker;
import com.gongbang.team.service.TeamMemberService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** STOMP 구독·송신 시 채팅방 참여자 및 활성 팀원 여부를 확인하는 검증기. */
@Component
@RequiredArgsConstructor
public class ChatStompAccessValidator implements ChatRoomAccessChecker {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final TeamMemberService teamMemberService;

    @Override
    @Transactional(readOnly = true)
    public boolean canAccessRoom(Long roomId, Long memberId) {
        if (roomId == null || memberId == null) {
            return false;
        }
        Optional<ChatRoom> roomOpt = chatRoomRepository.findById(roomId);
        if (roomOpt.isEmpty()) {
            return false;
        }
        ChatRoom room = roomOpt.get();
        return teamMemberService.isActiveMember(room.getTeamId(), memberId)
                && chatRoomMemberRepository.existsByIdChatRoomIdAndIdMemberId(roomId, memberId);
    }
}
