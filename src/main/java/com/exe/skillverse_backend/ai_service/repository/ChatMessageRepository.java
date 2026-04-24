package com.exe.skillverse_backend.ai_service.repository;

import com.exe.skillverse_backend.ai_service.entity.ChatMessage;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /**
     * Find all messages in a session ordered by creation time
     */
    @Query("SELECT cm FROM ChatMessage cm WHERE cm.chatSession.id = :sessionId ORDER BY cm.createdAt ASC")
    List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(@Param("sessionId") Long sessionId);

    /**
     * Find recent messages in a session (limited to avoid large context windows)
     * Uses Pageable to limit results - portable across database vendors
     */
    @Query("SELECT cm FROM ChatMessage cm WHERE cm.chatSession.id = :sessionId ORDER BY cm.createdAt ASC")
    List<ChatMessage> findRecentBySessionId(@Param("sessionId") Long sessionId, Pageable pageable);

    /**
     * Find latest message in a session
     */
    ChatMessage findFirstByChatSession_IdOrderByCreatedAtDesc(Long sessionId);

    /**
     * Find all sessions for a user ordered by latest message
     * Uses subquery to avoid PostgreSQL SELECT DISTINCT + ORDER BY conflict
     */
    @Query("SELECT cm.chatSession.id FROM ChatMessage cm WHERE cm.user.id = :userId " +
            "GROUP BY cm.chatSession.id ORDER BY MAX(cm.createdAt) DESC")
    List<Long> findSessionIdsByUserId(@Param("userId") Long userId);

    /**
     * Delete all messages in a session
     */
    void deleteByChatSession_Id(Long sessionId);

    /**
     * Count total distinct sessions in the system (Admin)
     */
    @Query("SELECT COUNT(DISTINCT cm.chatSession.id) FROM ChatMessage cm")
    Long countDistinctSessions();

    /**
     * Count total messages in the system (Admin)
     */
    @Query("SELECT COUNT(cm) FROM ChatMessage cm")
    Long countTotalMessages();

    long countByChatSession_Id(Long sessionId);
}
