package com.gongbang.chat.service;

import com.gongbang.chat.repository.*;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.service.AdminMemberService;
import com.gongbang.team.service.TeamMemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatReportService {
    private final ChatMessageRepository messages;
    private final ChatRoomRepository rooms;
    private final ChatRoomMemberRepository members;
    private final TeamMemberService teams;
    private final AdminMemberService access;

    public record Target(Long authorId, String content) {}

    @Transactional(readOnly = true)
    public Target read(Long actor, Long id, boolean admin) {
        var message =
                messages.findById(id)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (admin) access.requireAdmin(actor);
        else {
            var room =
                    rooms.findById(message.getChatRoomId())
                            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
            teams.requireMember(room.getTeamId(), actor);
            if (!members.existsByIdChatRoomIdAndIdMemberId(room.getId(), actor))
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return new Target(message.getSenderId(), message.getContent());
    }
}
