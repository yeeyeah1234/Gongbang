package com.gongbang.chat.controller;

import com.gongbang.auth.domain.LoginMember;
import com.gongbang.chat.dto.ChatMessagePageResponse;
import com.gongbang.chat.dto.ChatMessageReadRequest;
import com.gongbang.chat.dto.ChatRoomResponse;
import com.gongbang.chat.dto.DirectChatCreateRequest;
import com.gongbang.chat.dto.DirectChatCreateResponse;
import com.gongbang.chat.service.ChatCommandService;
import com.gongbang.chat.service.ChatQueryService;
import com.gongbang.global.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** NET-04·NET-05 채팅 REST API 컨트롤러. 권한 확인은 service가 담당한다. */
@RestController
@RequiredArgsConstructor
public class ChatController {

    private final ChatQueryService chatQueryService;
    private final ChatCommandService chatCommandService;

    /** NET-04 팀 내 내 채팅방 목록 조회. */
    @GetMapping("/api/teams/{teamId}/chat-rooms")
    public ApiResponse<List<ChatRoomResponse>> getChatRooms(
            @AuthenticationPrincipal LoginMember me, @PathVariable Long teamId) {
        return ApiResponse.success(chatQueryService.getChatRooms(teamId, me.getMemberId()));
    }

    /** NET-04 팀 내 1:1 채팅방 열기(있으면 기존 반환, 없으면 생성). */
    @PostMapping("/api/teams/{teamId}/chat-rooms/direct")
    public ApiResponse<DirectChatCreateResponse> openOrCreateDirectRoom(
            @AuthenticationPrincipal LoginMember me,
            @PathVariable Long teamId,
            @Valid @RequestBody DirectChatCreateRequest request) {
        return ApiResponse.success(
                chatCommandService.openOrCreateDirectRoom(
                        teamId, me.getMemberId(), request.memberId()));
    }

    /** NET-05 채팅방 메시지 커서 페이징 조회. */
    @GetMapping("/api/chat-rooms/{roomId}/messages")
    public ApiResponse<ChatMessagePageResponse> getMessages(
            @AuthenticationPrincipal LoginMember me,
            @PathVariable Long roomId,
            @RequestParam(required = false) Long before,
            @RequestParam(defaultValue = "30") int size) {
        return ApiResponse.success(
                chatQueryService.getMessages(roomId, me.getMemberId(), before, size));
    }

    /** NET-05 채팅방 읽음 처리. */
    @PatchMapping("/api/chat-rooms/{roomId}/read")
    public ApiResponse<Void> markAsRead(
            @AuthenticationPrincipal LoginMember me,
            @PathVariable Long roomId,
            @Valid @RequestBody ChatMessageReadRequest request) {
        chatCommandService.markAsRead(roomId, me.getMemberId(), request.lastReadMessageId());
        return ApiResponse.success(null);
    }
}
