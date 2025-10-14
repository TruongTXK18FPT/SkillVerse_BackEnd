package com.exe.skillverse_backend.ai_service.repository;

import com.exe.skillverse_backend.ai_service.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /**
     * Find all messages in a session ordered by creation time
     */
    @Query("SELECT cm FROM ChatMessage cm WHERE cm.sessionId = :sessionId ORDER BY cm.createdAt ASC")
    List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(@Param("sessionId") Long sessionId);

    /**
     * Find latest message in a session
     */
    @Query("SELECT cm FROM ChatMessage cm WHERE cm.sessionId = :sessionId ORDER BY cm.createdAt DESC LIMIT 1")
    ChatMessage findLatestBySessionId(@Param("sessionId") Long sessionId);

    /**
     * Find all sessions for a user ordered by latest message
     * Uses subquery to avoid PostgreSQL SELECT DISTINCT + ORDER BY conflict
     */
    @Query("SELECT cm.sessionId FROM ChatMessage cm WHERE cm.user.id = :userId " +
            "GROUP BY cm.sessionId ORDER BY MAX(cm.createdAt) DESC")
    List<Long> findSessionIdsByUserId(@Param("userId") Long userId);

    /**
     * Delete all messages in a session
     */
    void deleteBySessionId(Long sessionId);
}
