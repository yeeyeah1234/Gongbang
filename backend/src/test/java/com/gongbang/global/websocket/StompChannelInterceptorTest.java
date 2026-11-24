package com.gongbang.global.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

import com.gongbang.auth.jwt.JwtProvider;
import com.gongbang.member.domain.Member;
import com.gongbang.member.domain.MemberRole;
import com.gongbang.member.service.MemberService;
import com.gongbang.team.service.TeamMemberService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

@ExtendWith(MockitoExtension.class)
class StompChannelInterceptorTest {

    @Mock private JwtProvider jwtProvider;
    @Mock private MemberService memberService;
    @Mock private TeamMemberService teamMemberService;
    @Mock private ObjectProvider<ChatRoomAccessChecker> chatRoomAccessCheckerProvider;
    @Mock private ChatRoomAccessChecker chatRoomAccessChecker;
    @Mock private MessageChannel channel;
    @Mock private Member activeMember;

    private StompChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor =
                new StompChannelInterceptor(
                        jwtProvider,
                        memberService,
                        teamMemberService,
                        chatRoomAccessCheckerProvider);
    }

    @Test
    @DisplayName("CONNECT 시 유효한 Bearer 토큰과 활성 회원이면 StompPrincipal을 설정한다")
    void connect_성공() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer valid-token");
        accessor.setLeaveMutable(true);
        Message<byte[]> msg =
                MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        given(jwtProvider.parseAccessToken("valid-token"))
                .willReturn(Optional.of(new JwtProvider.AccessClaims(10L, MemberRole.USER)));
        given(memberService.findActiveMember(10L)).willReturn(Optional.of(activeMember));

        interceptor.preSend(msg, channel);

        assertThat(accessor.getUser()).isEqualTo(new StompPrincipal(10L, MemberRole.USER));
    }

    @Test
    @DisplayName("CONNECT 시 토큰이 없거나 비활성 회원이면 예외가 발생한다")
    void connect_실패_미인증_또는_탈퇴회원() {
        StompHeaderAccessor noHeader = StompHeaderAccessor.create(StompCommand.CONNECT);
        Message<byte[]> noHeaderMsg =
                MessageBuilder.createMessage(new byte[0], noHeader.getMessageHeaders());
        assertThrows(
                MessageDeliveryException.class, () -> interceptor.preSend(noHeaderMsg, channel));

        StompHeaderAccessor inactive = StompHeaderAccessor.create(StompCommand.CONNECT);
        inactive.setNativeHeader("Authorization", "Bearer ok-token");
        Message<byte[]> inactiveMsg =
                MessageBuilder.createMessage(new byte[0], inactive.getMessageHeaders());
        given(jwtProvider.parseAccessToken("ok-token"))
                .willReturn(Optional.of(new JwtProvider.AccessClaims(11L, MemberRole.USER)));
        given(memberService.findActiveMember(11L)).willReturn(Optional.empty());

        assertThrows(
                MessageDeliveryException.class, () -> interceptor.preSend(inactiveMsg, channel));
    }

    @Test
    @DisplayName("SUBSCRIBE 팀 이벤트와 채팅방 구독은 각각 팀원·채팅방 참여자만 허용한다")
    void subscribe_권한검증() {
        given(memberService.findActiveMember(10L)).willReturn(Optional.of(activeMember));
        given(memberService.findActiveMember(99L)).willReturn(Optional.of(activeMember));
        Message<byte[]> teamSub =
                frame(
                        StompCommand.SUBSCRIBE,
                        "/sub/teams/3/events",
                        new StompPrincipal(10L, MemberRole.USER));
        given(teamMemberService.isActiveMember(3L, 10L)).willReturn(true);
        interceptor.preSend(teamSub, channel);

        Message<byte[]> deniedTeamSub =
                frame(
                        StompCommand.SUBSCRIBE,
                        "/sub/teams/3/events",
                        new StompPrincipal(99L, MemberRole.USER));
        given(teamMemberService.isActiveMember(3L, 99L)).willReturn(false);
        assertThrows(
                MessageDeliveryException.class, () -> interceptor.preSend(deniedTeamSub, channel));

        Message<byte[]> roomSub =
                frame(
                        StompCommand.SUBSCRIBE,
                        "/sub/chat-rooms/7",
                        new StompPrincipal(10L, MemberRole.USER));
        given(chatRoomAccessCheckerProvider.getIfAvailable()).willReturn(chatRoomAccessChecker);
        given(chatRoomAccessChecker.canAccessRoom(7L, 10L)).willReturn(true);
        interceptor.preSend(roomSub, channel);

        given(chatRoomAccessChecker.canAccessRoom(7L, 99L)).willReturn(false);
        Message<byte[]> deniedRoomSub =
                frame(
                        StompCommand.SUBSCRIBE,
                        "/sub/chat-rooms/7",
                        new StompPrincipal(99L, MemberRole.USER));
        assertThrows(
                MessageDeliveryException.class, () -> interceptor.preSend(deniedRoomSub, channel));
    }

    @Test
    @DisplayName("SUBSCRIBE 개인 이벤트·채팅방 목록은 본인만 허용하고 미허용 경로는 차단한다")
    void subscribe_본인경로와_미허용경로() {
        given(memberService.findActiveMember(10L)).willReturn(Optional.of(activeMember));
        StompPrincipal user = new StompPrincipal(10L, MemberRole.USER);

        interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/sub/members/10/events", user), channel);
        assertThrows(
                MessageDeliveryException.class,
                () ->
                        interceptor.preSend(
                                frame(StompCommand.SUBSCRIBE, "/sub/members/11/events", user),
                                channel));

        given(teamMemberService.isActiveMember(5L, 10L)).willReturn(true);
        interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, "/sub/teams/5/members/10/chat-rooms", user), channel);
        assertThrows(
                MessageDeliveryException.class,
                () ->
                        interceptor.preSend(
                                frame(
                                        StompCommand.SUBSCRIBE,
                                        "/sub/teams/5/members/11/chat-rooms",
                                        user),
                                channel));

        assertThrows(
                MessageDeliveryException.class,
                () ->
                        interceptor.preSend(
                                frame(StompCommand.SUBSCRIBE, "/sub/etc/1", user), channel));
    }

    @Test
    @DisplayName("SEND 시 채팅방 송신 경로는 방 참여자만 허용한다")
    void send_권한검증() {
        given(memberService.findActiveMember(10L)).willReturn(Optional.of(activeMember));
        StompPrincipal user = new StompPrincipal(10L, MemberRole.USER);
        Message<byte[]> sendMsg = frame(StompCommand.SEND, "/pub/chat-rooms/7/send", user);

        given(chatRoomAccessCheckerProvider.getIfAvailable()).willReturn(chatRoomAccessChecker);
        given(chatRoomAccessChecker.canAccessRoom(7L, 10L)).willReturn(true);
        interceptor.preSend(sendMsg, channel);

        given(chatRoomAccessChecker.canAccessRoom(7L, 10L)).willReturn(false);
        assertThrows(MessageDeliveryException.class, () -> interceptor.preSend(sendMsg, channel));
    }

    @Test
    void suspendedMemberCannotSendOnExistingSession() {
        given(memberService.findActiveMember(10L)).willReturn(Optional.of(activeMember));
        given(activeMember.isSuspended()).willReturn(true);
        assertThrows(
                MessageDeliveryException.class,
                () ->
                        interceptor.preSend(
                                frame(
                                        StompCommand.SEND,
                                        "/pub/chat-rooms/7/send",
                                        new StompPrincipal(10L, MemberRole.USER)),
                                channel));
    }

    private static Message<byte[]> frame(
            StompCommand command, String destination, StompPrincipal principal) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setDestination(destination);
        accessor.setUser(principal);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
