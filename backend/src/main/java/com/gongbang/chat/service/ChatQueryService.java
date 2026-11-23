package com.gongbang.chat.service;

import com.gongbang.chat.domain.ChatMessage;
import com.gongbang.chat.domain.ChatRoom;
import com.gongbang.chat.domain.ChatRoomMember;
import com.gongbang.chat.domain.ChatRoomMemberId;
import com.gongbang.chat.domain.ChatRoomType;
import com.gongbang.chat.dto.ChatMessagePageResponse;
import com.gongbang.chat.dto.ChatMessageResponse;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** NET-04·NET-05 채팅 조회 서비스. */
@Service
@RequiredArgsConstructor
public class ChatQueryService {

    private static final int DEFAULT_PAGE_SIZE = 30;
    private static final int MAX_PAGE_SIZE = 100;

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final TeamMemberService teamMemberService;
    private final TeamService teamService;
    private final MemberService memberService;
    private final ChatMembershipService chatMembershipService;
    private final ChatMessageMapper chatMessageMapper;

    /** NET-04 내가 참여한 팀 채팅방 목록을 최근 활동순으로 조회한다. */
    @Transactional
    public List<ChatRoomResponse> getChatRooms(Long teamId, Long memberId) {
        teamMemberService.requireMember(teamId, memberId);
        chatMembershipService.ensureTeamRoomsInitialized(teamId);

        List<ChatRoom> rooms = chatRoomRepository.findJoinedRoomsOrderByRecent(teamId, memberId);
        if (rooms.isEmpty()) {
            return List.of();
        }

        List<Long> roomIds = rooms.stream().map(ChatRoom::getId).toList();
        Map<Long, List<ChatRoomMember>> membersByRoom = groupMembersByRoom(roomIds);
        Map<Long, ChatMessage> latestMessageByRoom = loadLatestMessagesByRoom(roomIds);
        Map<Long, MemberProfileSummary> profiles =
                loadProfilesForRooms(rooms, membersByRoom, latestMessageByRoom, memberId);
        String teamName = teamService.getTeamName(teamId);

        List<ChatRoomResponse> result = new ArrayList<>(rooms.size());
        for (ChatRoom room : rooms) {
            result.add(
                    toChatRoomResponse(
                            room,
                            memberId,
                            teamName,
                            membersByRoom.getOrDefault(room.getId(), List.of()),
                            latestMessageByRoom.get(room.getId()),
                            profiles));
        }
        return result;
    }

    /** NET-05 채팅방 메시지 목록을 커서(`before`) 기준으로 조회한다. 반환 목록은 오래된 순(오름차순)이다. */
    @Transactional(readOnly = true)
    public ChatMessagePageResponse getMessages(
            Long roomId, Long memberId, Long beforeMessageId, int size) {
        ChatRoom room =
                chatRoomRepository
                        .findById(roomId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        teamMemberService.requireMember(room.getTeamId(), memberId);
        if (!chatRoomMemberRepository.existsById(new ChatRoomMemberId(roomId, memberId))) {
            throw new BusinessException(ErrorCode.NOT_CHAT_ROOM_MEMBER);
        }

        int pageSize = clampPageSize(size);
        PageRequest pageable = PageRequest.of(0, pageSize + 1);
        List<ChatMessage> fetched =
                beforeMessageId == null
                        ? chatMessageRepository.findByChatRoomIdOrderByIdDesc(roomId, pageable)
                        : chatMessageRepository.findByChatRoomIdAndIdLessThanOrderByIdDesc(
                                roomId, beforeMessageId, pageable);

        boolean hasNext = fetched.size() > pageSize;
        List<ChatMessage> sliced =
                new ArrayList<>(hasNext ? fetched.subList(0, pageSize) : fetched);
        Collections.reverse(sliced);

        Set<Long> senderIds = new LinkedHashSet<>();
        Set<Long> fileIds = new LinkedHashSet<>();
        for (ChatMessage msg : sliced) {
            if (msg.getSenderId() != null) {
                senderIds.add(msg.getSenderId());
            }
            if (msg.getFileId() != null) {
                fileIds.add(msg.getFileId());
            }
        }

        Map<Long, MemberProfileSummary> profiles = memberService.getProfileSummaries(senderIds);
        Map<Long, String> fileUrls = chatMessageMapper.resolveFileUrls(fileIds);
        List<ChatMessageResponse> items =
                sliced.stream()
                        .map(msg -> chatMessageMapper.toMessageResponse(msg, profiles, fileUrls))
                        .toList();

        return new ChatMessagePageResponse(items, hasNext);
    }

    private Map<Long, List<ChatRoomMember>> groupMembersByRoom(List<Long> roomIds) {
        Map<Long, List<ChatRoomMember>> grouped = new HashMap<>();
        for (ChatRoomMember crm : chatRoomMemberRepository.findAllByIdChatRoomIdIn(roomIds)) {
            grouped.computeIfAbsent(crm.getId().getChatRoomId(), k -> new ArrayList<>()).add(crm);
        }
        return grouped;
    }

    private Map<Long, ChatMessage> loadLatestMessagesByRoom(List<Long> roomIds) {
        Map<Long, ChatMessage> map = new HashMap<>();
        for (ChatMessage message : chatMessageRepository.findLatestMessagesByRoomIds(roomIds)) {
            map.put(message.getChatRoomId(), message);
        }
        return map;
    }

    private Map<Long, MemberProfileSummary> loadProfilesForRooms(
            List<ChatRoom> rooms,
            Map<Long, List<ChatRoomMember>> membersByRoom,
            Map<Long, ChatMessage> latestMessageByRoom,
            Long myMemberId) {
        Set<Long> memberIds = new LinkedHashSet<>();
        for (ChatRoom room : rooms) {
            if (room.getType() == ChatRoomType.DIRECT) {
                findPartnerId(membersByRoom.getOrDefault(room.getId(), List.of()), myMemberId)
                        .ifPresent(memberIds::add);
            }
            ChatMessage latest = latestMessageByRoom.get(room.getId());
            if (latest != null && latest.getSenderId() != null) {
                memberIds.add(latest.getSenderId());
            }
        }
        return memberService.getProfileSummaries(memberIds);
    }

    private ChatRoomResponse toChatRoomResponse(
            ChatRoom room,
            Long myMemberId,
            String teamName,
            List<ChatRoomMember> members,
            ChatMessage lastMessage,
            Map<Long, MemberProfileSummary> profiles) {
        ChatRoomResponse.PartnerInfo partner = null;
        String roomName = teamName;

        if (room.getType() == ChatRoomType.DIRECT) {
            Long partnerId = findPartnerId(members, myMemberId).orElse(myMemberId);
            MemberProfileSummary profile = profiles.get(partnerId);
            String nickname = profile != null ? profile.nickname() : "탈퇴한 사용자";
            String profileImageUrl = profile != null ? profile.profileImageUrl() : null;
            partner = new ChatRoomResponse.PartnerInfo(partnerId, nickname, profileImageUrl);
            roomName = nickname;
        }

        ChatRoomResponse.LastMessageInfo lastMessageInfo =
                chatMessageMapper.toLastMessageInfo(lastMessage, profiles);
        long unreadCount = computeUnreadCount(room.getId(), myMemberId, members, lastMessage);

        return new ChatRoomResponse(
                room.getId(), room.getType(), roomName, partner, lastMessageInfo, unreadCount);
    }

    private long computeUnreadCount(
            Long roomId, Long myMemberId, List<ChatRoomMember> members, ChatMessage lastMessage) {
        if (lastMessage == null) {
            return 0L;
        }
        Long lastReadId =
                members.stream()
                        .filter(crm -> Objects.equals(crm.getId().getMemberId(), myMemberId))
                        .map(ChatRoomMember::getLastReadMessageId)
                        .findFirst()
                        .orElse(null);

        if (lastReadId != null && lastReadId >= lastMessage.getId()) {
            return 0L;
        }
        return lastReadId == null
                ? chatMessageRepository.countByChatRoomId(roomId)
                : chatMessageRepository.countByChatRoomIdAndIdGreaterThan(roomId, lastReadId);
    }

    private static java.util.Optional<Long> findPartnerId(
            List<ChatRoomMember> members, Long myMemberId) {
        return members.stream()
                .map(crm -> crm.getId().getMemberId())
                .filter(id -> !Objects.equals(id, myMemberId))
                .findFirst();
    }

    private static int clampPageSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
