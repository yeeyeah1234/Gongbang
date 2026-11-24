package com.gongbang.global.websocket;

import com.gongbang.auth.jwt.JwtProvider;
import com.gongbang.member.service.MemberService;
import com.gongbang.team.service.TeamMemberService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * STOMP 인바운드 채널 인증·인가 인터셉터.
 *
 * <ul>
 *   <li>CONNECT: {@code Authorization: Bearer ...} 액세스 토큰과 활성 회원 여부를 검증해 {@link StompPrincipal} 설정
 *   <li>SUBSCRIBE: 팀·채팅방·개인 이벤트 구독 경로 권한 검증
 *   <li>SEND: 인증된 사용자 및 채팅방 송신 경로 권한 검증
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class StompChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final Pattern TEAM_EVENTS_PATTERN =
            Pattern.compile("^/sub/teams/(\\d+)/events$");
    private static final Pattern CHAT_ROOM_PATTERN = Pattern.compile("^/sub/chat-rooms/(\\d+)$");
    private static final Pattern MEMBER_EVENTS_PATTERN =
            Pattern.compile("^/sub/members/(\\d+)/events$");
    private static final Pattern CHAT_ROOM_LIST_PATTERN =
            Pattern.compile("^/sub/teams/(\\d+)/members/(\\d+)/chat-rooms$");
    private static final Pattern CHAT_SEND_PATTERN =
            Pattern.compile("^/pub/chat-rooms/(\\d+)/send$");

    private final JwtProvider jwtProvider;
    private final MemberService memberService;
    private final TeamMemberService teamMemberService;
    private final ObjectProvider<ChatRoomAccessChecker> chatRoomAccessCheckerProvider;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();
        if (command == StompCommand.CONNECT) {
            authenticate(accessor);
        } else if (command == StompCommand.SUBSCRIBE) {
            StompPrincipal principal = requirePrincipal(accessor);
            authorizeSubscription(accessor.getDestination(), principal);
        } else if (command == StompCommand.SEND) {
            StompPrincipal principal = requirePrincipal(accessor);
            authorizeSend(accessor.getDestination(), principal);
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null) {
            header = accessor.getFirstNativeHeader("authorization");
        }
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            throw new MessageDeliveryException("STOMP 연결에는 Bearer 액세스 토큰이 필요합니다.");
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        JwtProvider.AccessClaims claims =
                jwtProvider
                        .parseAccessToken(token)
                        .orElseThrow(() -> new MessageDeliveryException("유효하지 않거나 만료된 토큰입니다."));
        if (memberService
                .findActiveMember(claims.memberId())
                .filter(m -> !m.isSuspended())
                .isEmpty()) {
            throw new MessageDeliveryException("유효하지 않거나 탈퇴한 회원입니다.");
        }
        accessor.setUser(new StompPrincipal(claims.memberId(), claims.role()));
    }

    private void authorizeSubscription(String destination, StompPrincipal principal) {
        requireNotSuspended(principal.memberId());
        if (destination == null || destination.isBlank()) {
            throw new MessageDeliveryException("구독 경로가 비어 있습니다.");
        }

        Matcher teamEvents = TEAM_EVENTS_PATTERN.matcher(destination);
        if (teamEvents.matches()) {
            long teamId = Long.parseLong(teamEvents.group(1));
            if (!teamMemberService.isActiveMember(teamId, principal.memberId())) {
                throw new MessageDeliveryException("팀원만 팀 이벤트를 구독할 수 있습니다.");
            }
            return;
        }

        Matcher chatRoom = CHAT_ROOM_PATTERN.matcher(destination);
        if (chatRoom.matches()) {
            long roomId = Long.parseLong(chatRoom.group(1));
            requireRoomAccess(roomId, principal.memberId(), "채팅방 참여자만 구독할 수 있습니다.");
            return;
        }

        Matcher memberEvents = MEMBER_EVENTS_PATTERN.matcher(destination);
        if (memberEvents.matches()) {
            long memberId = Long.parseLong(memberEvents.group(1));
            if (!principal.memberId().equals(memberId)) {
                throw new MessageDeliveryException("본인의 개인 이벤트만 구독할 수 있습니다.");
            }
            return;
        }

        Matcher chatRoomList = CHAT_ROOM_LIST_PATTERN.matcher(destination);
        if (chatRoomList.matches()) {
            long teamId = Long.parseLong(chatRoomList.group(1));
            long memberId = Long.parseLong(chatRoomList.group(2));
            if (!principal.memberId().equals(memberId)
                    || !teamMemberService.isActiveMember(teamId, memberId)) {
                throw new MessageDeliveryException("해당 팀의 본인 채팅방 목록만 구독할 수 있습니다.");
            }
            return;
        }

        throw new MessageDeliveryException("허용되지 않은 구독 경로입니다.");
    }

    private void authorizeSend(String destination, StompPrincipal principal) {
        requireNotSuspended(principal.memberId());
        if (destination == null || destination.isBlank()) {
            throw new MessageDeliveryException("전송 경로가 비어 있습니다.");
        }
        Matcher chatSend = CHAT_SEND_PATTERN.matcher(destination);
        if (chatSend.matches()) {
            long roomId = Long.parseLong(chatSend.group(1));
            requireRoomAccess(roomId, principal.memberId(), "채팅방 참여자만 메시지를 보낼 수 있습니다.");
        }
    }

    private void requireRoomAccess(Long roomId, Long memberId, String deniedMessage) {
        ChatRoomAccessChecker checker = chatRoomAccessCheckerProvider.getIfAvailable();
        if (checker == null || !checker.canAccessRoom(roomId, memberId)) {
            throw new MessageDeliveryException(deniedMessage);
        }
    }

    private static StompPrincipal requirePrincipal(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof StompPrincipal principal) {
            return principal;
        }
        throw new MessageDeliveryException("인증된 STOMP 세션이 아닙니다.");
    }

    private void requireNotSuspended(Long id) {
        if (memberService.findActiveMember(id).filter(m -> !m.isSuspended()).isEmpty())
            throw new MessageDeliveryException("활성 상태이며 이용 정지되지 않은 회원만 사용할 수 있습니다.");
    }
}
