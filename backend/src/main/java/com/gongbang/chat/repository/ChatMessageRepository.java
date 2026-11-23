package com.gongbang.chat.repository;

import com.gongbang.chat.domain.ChatMessage;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 채팅 메시지 JPA 레포지토리. */
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByChatRoomIdOrderByIdDesc(Long chatRoomId, Pageable pageable);

    List<ChatMessage> findByChatRoomIdAndIdLessThanOrderByIdDesc(
            Long chatRoomId, Long beforeId, Pageable pageable);

    long countByChatRoomId(Long chatRoomId);

    long countByChatRoomIdAndIdGreaterThan(Long chatRoomId, Long id);

    boolean existsByIdAndChatRoomId(Long id, Long chatRoomId);

    /** 여러 채팅방의 가장 마지막 메시지 1건씩을 한 번에 조회한다. */
    @Query(
            "select m from ChatMessage m where m.id in ("
                    + "select max(m2.id) from ChatMessage m2 where m2.chatRoomId in :roomIds group by m2.chatRoomId"
                    + ")")
    List<ChatMessage> findLatestMessagesByRoomIds(@Param("roomIds") Collection<Long> roomIds);
}
