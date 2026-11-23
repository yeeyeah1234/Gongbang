package com.gongbang.chat.repository;

import com.gongbang.chat.domain.ChatRoom;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 채팅방 JPA 레포지토리. */
public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    Optional<ChatRoom> findByRoomKey(String roomKey);

    List<ChatRoom> findAllByTeamId(Long teamId);

    /** 특정 팀에서 회원이 참여 중인 채팅방 목록을 최신 활동순(마지막 메시지 → 생성 시각)으로 조회한다. */
    @Query(
            "select r from ChatRoom r"
                    + " join ChatRoomMember crm on crm.id.chatRoomId = r.id"
                    + " where r.teamId = :teamId and crm.id.memberId = :memberId"
                    + " order by coalesce(r.lastMessageAt, r.createdAt) desc, r.id desc")
    List<ChatRoom> findJoinedRoomsOrderByRecent(
            @Param("teamId") Long teamId, @Param("memberId") Long memberId);
}
