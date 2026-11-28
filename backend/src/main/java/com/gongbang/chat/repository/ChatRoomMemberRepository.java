package com.gongbang.chat.repository;

import com.gongbang.chat.domain.ChatRoomMember;
import com.gongbang.chat.domain.ChatRoomMemberId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 채팅방 참여자 JPA 레포지토리. */
public interface ChatRoomMemberRepository extends JpaRepository<ChatRoomMember, ChatRoomMemberId> {

    void deleteByIdMemberId(Long memberId);

    boolean existsByIdChatRoomIdAndIdMemberId(Long chatRoomId, Long memberId);

    List<ChatRoomMember> findAllByIdChatRoomId(Long chatRoomId);

    List<ChatRoomMember> findAllByIdChatRoomIdIn(Collection<Long> chatRoomIds);

    List<ChatRoomMember> findAllByIdChatRoomIdInAndIdMemberId(
            Collection<Long> chatRoomIds, Long memberId);

    void deleteByIdChatRoomIdInAndIdMemberId(Collection<Long> chatRoomIds, Long memberId);
}
