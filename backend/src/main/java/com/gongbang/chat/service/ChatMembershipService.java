package com.gongbang.chat.service;

import com.gongbang.chat.domain.ChatRoom;
import com.gongbang.chat.domain.ChatRoomMember;
import com.gongbang.chat.domain.ChatRoomType;
import com.gongbang.chat.repository.ChatRoomMemberRepository;
import com.gongbang.chat.repository.ChatRoomRepository;
import com.gongbang.member.service.MemberService;
import com.gongbang.team.service.TeamService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 채팅방 생명주기 및 참여자 동기화 서비스 (INT-006 / NET-06).
 *
 * <p>팀 생성, 팀원 확정, 강제 퇴장·탈퇴 시 팀 채팅방과 1:1 채팅방 참여자를 일관되게 관리한다.
 */
@Service
@RequiredArgsConstructor
public class ChatMembershipService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final TeamService teamService;
    private final MemberService memberService;
    private final ChatRealtimeService chatRealtimeService;

    /** 팀 전체 채팅방(`T:{teamId}`)을 만들고 팀장(및 현재 팀원)을 참여자로 넣는다. 이미 있으면 기존 방 ID를 반환한다. */
    @Transactional
    public Long createTeamRoom(Long teamId, Long leaderMemberId) {
        ChatRoom room = getOrCreateTeamRoom(teamId);
        if (leaderMemberId != null) {
            ensureMembership(room.getId(), leaderMemberId);
        }
        return room.getId();
    }

    /** 팀에 새로 합류한 팀원을 팀 전체 채팅방에 넣고 입장 시스템 메시지를 남긴다. */
    @Transactional
    public void addMemberToTeamRoom(Long teamId, Long memberId) {
        ChatRoom room = getOrCreateTeamRoom(teamId);
        boolean alreadyJoined =
                chatRoomMemberRepository.existsByIdChatRoomIdAndIdMemberId(room.getId(), memberId);
        if (!alreadyJoined) {
            chatRoomMemberRepository.save(ChatRoomMember.create(room.getId(), memberId));
            String nickname = memberService.displayName(memberId);
            chatRealtimeService.sendSystemMessage(teamId, nickname + "님이 입장했습니다.");
        }
    }

    /** 기존에 팀 채팅방이 없는 레거시 팀이라도 목록 조회 시 팀 전체 채팅방과 현재 팀원 참여자를 맞춰 준다. */
    @Transactional
    public void ensureTeamRoomsInitialized(Long teamId) {
        ChatRoom teamRoom = getOrCreateTeamRoom(teamId);
        for (Long activeMemberId : teamService.findActiveMemberIds(teamId)) {
            ensureMembership(teamRoom.getId(), activeMemberId);
        }
    }

    /** 강제 퇴장·탈퇴한 회원을 해당 팀의 모든 채팅방(`TEAM`, `DIRECT`) 참여자에서 제거한다. */
    @Transactional
    public void removeMemberFromTeamRooms(Long teamId, Long memberId) {
        List<Long> roomIds =
                chatRoomRepository.findAllByTeamId(teamId).stream().map(ChatRoom::getId).toList();
        if (!roomIds.isEmpty()) {
            chatRoomMemberRepository.deleteByIdChatRoomIdInAndIdMemberId(roomIds, memberId);
        }
    }

    private ChatRoom getOrCreateTeamRoom(Long teamId) {
        String roomKey = ChatRoom.teamRoomKey(teamId);
        return chatRoomRepository
                .findByRoomKey(roomKey)
                .orElseGet(
                        () ->
                                chatRoomRepository.save(
                                        ChatRoom.create(teamId, ChatRoomType.TEAM, roomKey)));
    }

    private void ensureMembership(Long roomId, Long memberId) {
        if (!chatRoomMemberRepository.existsByIdChatRoomIdAndIdMemberId(roomId, memberId)) {
            chatRoomMemberRepository.save(ChatRoomMember.create(roomId, memberId));
        }
    }
}
