package com.exe.skillverse_backend.ai_service.repository;

import com.exe.skillverse_backend.ai_service.entity.ChatSession;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    Optional<ChatSession> findByIdAndUser_Id(Long sessionId, Long userId);

    @Query("""
        SELECT cs
        FROM ChatSession cs
        WHERE cs.user.id = :userId
        ORDER BY cs.lastMessageAt DESC, cs.createdAt DESC
    """)
    List<ChatSession> findByUserIdOrderByActivityDesc(@Param("userId") Long userId);
}
