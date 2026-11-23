package com.gongbang.chat.service;

import com.gongbang.chat.domain.ChatMessage;
import com.gongbang.chat.domain.ChatRoom;
import com.gongbang.chat.domain.ChatRoomMember;
import com.gongbang.chat.domain.ChatRoomMemberId;
import com.gongbang.chat.domain.ChatRoomType;
import com.gongbang.chat.dto.ChatRoomResponse;
import com.gongbang.chat.dto.DirectChatCreateResponse;
import com.gongbang.chat.repository.ChatMessageRepository;
import com.gongbang.chat.repository.ChatRoomMemberRepository;
import com.gongbang.chat.repository.ChatRoomRepository;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.dto.MemberProfileSummary;
import com.gongbang.member.service.MemberService;
import com.gongbang.team.service.TeamMemberService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** NET-04·NET-05 채팅방 열기 및 읽음 처리 커맨드 서비스. */
@Service
@RequiredArgsConstructor
public class ChatCommandService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final TeamMemberService teamMemberService;
    private final MemberService memberService;
    private final ChatMessageMapper chatMessageMapper;
    private final ApplicationEventPublisher eventPublisher;

    /** NET-04 팀 내 1:1 채팅방을 열거나 기존 방 ID를 반환한다. */
    @Transactional
    public DirectChatCreateResponse openOrCreateDirectRoom(
            Long teamId, Long myMemberId, Long targetMemberId) {
        if (myMemberId.equals(targetMemberId)) {
            throw new BusinessException(ErrorCode.CANNOT_CHAT_SELF);
        }
        teamMemberService.requireMember(teamId, myMemberId);
        teamMemberService.requireMember(teamId, targetMemberId);
        if (memberService.findActiveMember(targetMemberId).isEmpty()) {
            throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
        }

        String roomKey = ChatRoom.directRoomKey(teamId, myMemberId, targetMemberId);
        ChatRoom room =
                chatRoomRepository
                        .findByRoomKey(roomKey)
                        .orElseGet(
                                () ->
                                        chatRoomRepository.save(
                                                ChatRoom.create(
                                                        teamId, ChatRoomType.DIRECT, roomKey)));

        ensureParticipant(room.getId(), myMemberId);
        ensureParticipant(room.getId(), targetMemberId);
        return new DirectChatCreateResponse(room.getId());
    }

    /** NET-05 채팅방에서 마지막으로 읽은 메시지 ID를 단조 증가 형태로 기록한다. */
    @Transactional
    public void markAsRead(Long roomId, Long memberId, Long lastReadMessageId) {
        ChatRoom room =
                chatRoomRepository
                        .findById(roomId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        teamMemberService.requireMember(room.getTeamId(), memberId);

        ChatRoomMember crm =
                chatRoomMemberRepository
                        .findById(new ChatRoomMemberId(roomId, memberId))
                        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_CHAT_ROOM_MEMBER));

        if (!chatMessageRepository.existsByIdAndChatRoomId(lastReadMessageId, roomId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "해당 채팅방의 메시지가 아닙니다.");
        }

        crm.updateLastReadMessageId(lastReadMessageId);

        List<ChatMessage> latestList =
                chatMessageRepository.findLatestMessagesByRoomIds(List.of(roomId));
        ChatRoomResponse.LastMessageInfo lastMessageInfo = null;
        LocalDateTime updatedAt = LocalDateTime.now();
        if (!latestList.isEmpty()) {
            ChatMessage latest = latestList.get(0);
            Map<Long, MemberProfileSummary> profiles =
                    latest.getSenderId() != null
                            ? memberService.getProfileSummaries(List.of(latest.getSenderId()))
                            : Map.of();
            lastMessageInfo = chatMessageMapper.toLastMessageInfo(latest, profiles);
            if (latest.getCreatedAt() != null) {
                updatedAt = latest.getCreatedAt();
            }
        }

        eventPublisher.publishEvent(
                new ChatRoomReadEvent(
                        room.getTeamId(), roomId, memberId, lastMessageInfo, updatedAt));
    }

    private void ensureParticipant(Long roomId, Long memberId) {
        if (!chatRoomMemberRepository.existsByIdChatRoomIdAndIdMemberId(roomId, memberId)) {
            chatRoomMemberRepository.save(ChatRoomMember.create(roomId, memberId));
        }
    }
}
