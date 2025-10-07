package com.exe.skillverse_backend.ai_service.repository;

import com.exe.skillverse_backend.ai_service.entity.UserRoadmapProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRoadmapProgressRepository extends JpaRepository<UserRoadmapProgress, Long> {

    /**
     * Find progress for a specific quest in a roadmap session
     */
    @Query("SELECT urp FROM UserRoadmapProgress urp WHERE urp.roadmapSession.id = :sessionId AND urp.questId = :questId")
    Optional<UserRoadmapProgress> findBySessionIdAndQuestId(@Param("sessionId") Long sessionId,
            @Param("questId") String questId);

    /**
     * Find all progress entries for a roadmap session
     */
    @Query("SELECT urp FROM UserRoadmapProgress urp WHERE urp.roadmapSession.id = :sessionId")
    List<UserRoadmapProgress> findBySessionId(@Param("sessionId") Long sessionId);

    /**
     * Count completed quests in a roadmap session
     */
    @Query("SELECT COUNT(urp) FROM UserRoadmapProgress urp WHERE urp.roadmapSession.id = :sessionId AND urp.status = 'COMPLETED'")
    Long countCompletedBySessionId(@Param("sessionId") Long sessionId);
}
