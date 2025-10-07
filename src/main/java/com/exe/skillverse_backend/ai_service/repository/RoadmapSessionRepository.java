package com.exe.skillverse_backend.ai_service.repository;

import com.exe.skillverse_backend.ai_service.entity.RoadmapSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoadmapSessionRepository extends JpaRepository<RoadmapSession, Long> {

    /**
     * Find all roadmap sessions for a specific user, ordered by creation date
     * (newest first)
     */
    @Query("SELECT rs FROM RoadmapSession rs WHERE rs.user.id = :userId ORDER BY rs.createdAt DESC")
    List<RoadmapSession> findByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId);

    /**
     * Find a specific roadmap session by ID and user ID (for security)
     */
    @Query("SELECT rs FROM RoadmapSession rs WHERE rs.id = :sessionId AND rs.user.id = :userId")
    Optional<RoadmapSession> findByIdAndUserId(@Param("sessionId") Long sessionId, @Param("userId") Long userId);

    /**
     * Count total roadmaps for a user
     */
    @Query("SELECT COUNT(rs) FROM RoadmapSession rs WHERE rs.user.id = :userId")
    Long countByUserId(@Param("userId") Long userId);
}
