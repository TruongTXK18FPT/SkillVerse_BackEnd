package com.exe.skillverse_backend.ai_service.repository;

import com.exe.skillverse_backend.ai_service.entity.RoadmapSession;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RoadmapSessionRepository extends JpaRepository<RoadmapSession, Long> {

    /**
     * Find all roadmap sessions for a specific user, ordered by creation date
     * (newest first)
     */
    @Query("SELECT rs FROM RoadmapSession rs WHERE rs.user.id = :userId ORDER BY rs.createdAt DESC")
    List<RoadmapSession> findByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId);

    /**
     * Find all roadmap sessions ordered by creation date (newest first)
     */
    List<RoadmapSession> findAllByOrderByCreatedAtDesc();

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

    Long countByRoadmapMode(String roadmapMode);

    Long countByUser_IdAndRoadmapMode(Long userId, String roadmapMode);

    @Query("SELECT rs.roadmapMode, COUNT(rs) FROM RoadmapSession rs GROUP BY rs.roadmapMode")
    List<Object[]> countGroupedByMode();

    @Query("SELECT rs.roadmapMode, COUNT(rs) FROM RoadmapSession rs WHERE rs.user.id = :userId GROUP BY rs.roadmapMode")
    List<Object[]> countGroupedByModeForUser(@Param("userId") Long userId);

    @Query("SELECT rs.roadmapMode, COUNT(rs) FROM RoadmapSession rs WHERE rs.createdAt BETWEEN :from AND :to GROUP BY rs.roadmapMode")
    List<Object[]> countGroupedByModeInRange(@Param("from") Instant from, @Param("to") Instant to);

    @Query("SELECT rs.roadmapMode, COUNT(rs) FROM RoadmapSession rs WHERE rs.user.id = :userId AND rs.createdAt BETWEEN :from AND :to GROUP BY rs.roadmapMode")
    List<Object[]> countGroupedByModeInRangeForUser(@Param("userId") Long userId, @Param("from") Instant from, @Param("to") Instant to);

    @Query(value = "SELECT date_trunc('day', created_at AT TIME ZONE 'Asia/Ho_Chi_Minh') AS bucket, roadmap_mode, COUNT(*) AS cnt " +
            "FROM roadmap_sessions WHERE created_at BETWEEN :from AND :to " +
            "GROUP BY bucket, roadmap_mode ORDER BY bucket", nativeQuery = true)
    List<Object[]> countModeDaily(@Param("from") Instant from, @Param("to") Instant to);

    @Query(value = "SELECT date_trunc('day', created_at AT TIME ZONE 'Asia/Ho_Chi_Minh') AS bucket, roadmap_mode, COUNT(*) AS cnt " +
            "FROM roadmap_sessions WHERE user_id = :userId AND created_at BETWEEN :from AND :to " +
            "GROUP BY bucket, roadmap_mode ORDER BY bucket", nativeQuery = true)
    List<Object[]> countModeDailyForUser(@Param("userId") Long userId, @Param("from") Instant from, @Param("to") Instant to);

    @Query(value = "SELECT date_trunc('week', created_at AT TIME ZONE 'Asia/Ho_Chi_Minh') AS bucket, roadmap_mode, COUNT(*) AS cnt " +
            "FROM roadmap_sessions WHERE created_at BETWEEN :from AND :to " +
            "GROUP BY bucket, roadmap_mode ORDER BY bucket", nativeQuery = true)
    List<Object[]> countModeWeekly(@Param("from") Instant from, @Param("to") Instant to);

    @Query(value = "SELECT date_trunc('week', created_at AT TIME ZONE 'Asia/Ho_Chi_Minh') AS bucket, roadmap_mode, COUNT(*) AS cnt " +
            "FROM roadmap_sessions WHERE user_id = :userId AND created_at BETWEEN :from AND :to " +
            "GROUP BY bucket, roadmap_mode ORDER BY bucket", nativeQuery = true)
    List<Object[]> countModeWeeklyForUser(@Param("userId") Long userId, @Param("from") Instant from, @Param("to") Instant to);

    @Query(value = "SELECT date_trunc('month', created_at AT TIME ZONE 'Asia/Ho_Chi_Minh') AS bucket, roadmap_mode, COUNT(*) AS cnt " +
            "FROM roadmap_sessions WHERE created_at BETWEEN :from AND :to " +
            "GROUP BY bucket, roadmap_mode ORDER BY bucket", nativeQuery = true)
    List<Object[]> countModeMonthly(@Param("from") Instant from, @Param("to") Instant to);

    @Query(value = "SELECT date_trunc('month', created_at AT TIME ZONE 'Asia/Ho_Chi_Minh') AS bucket, roadmap_mode, COUNT(*) AS cnt " +
            "FROM roadmap_sessions WHERE user_id = :userId AND created_at BETWEEN :from AND :to " +
            "GROUP BY bucket, roadmap_mode ORDER BY bucket", nativeQuery = true)
    List<Object[]> countModeMonthlyForUser(@Param("userId") Long userId, @Param("from") Instant from, @Param("to") Instant to);

    /**
     * Find the ACTIVE roadmap for a user (should be at most 1)
     */
    @Query("SELECT rs FROM RoadmapSession rs WHERE rs.user.id = :userId AND rs.status = 'ACTIVE'")
    Optional<RoadmapSession> findActiveByUserId(@Param("userId") Long userId);

    /**
     * Find all non-deleted roadmaps for a user
     */
    @Query("SELECT rs FROM RoadmapSession rs WHERE rs.user.id = :userId AND rs.status <> 'DELETED' ORDER BY rs.createdAt DESC")
    List<RoadmapSession> findByUserIdAndStatusNotDeleted(@Param("userId") Long userId);

        /**
         * Find all soft-deleted roadmaps for a user
         */
        @Query("SELECT rs FROM RoadmapSession rs WHERE rs.user.id = :userId AND rs.status = 'DELETED' ORDER BY rs.createdAt DESC")
        List<RoadmapSession> findByUserIdAndStatusDeleted(@Param("userId") Long userId);

    /**
     * Count all non-deleted roadmaps for a user (for storage limit check)
     */
    @Query("SELECT COUNT(rs) FROM RoadmapSession rs WHERE rs.user.id = :userId AND rs.status <> 'DELETED'")
    Long countByUserIdAndStatusNotDeleted(@Param("userId") Long userId);

        /**
         * Group roadmap counts by lifecycle status for current user
         */
        @Query("SELECT rs.status, COUNT(rs) FROM RoadmapSession rs WHERE rs.user.id = :userId GROUP BY rs.status")
        List<Object[]> countGroupedByStatusForUser(@Param("userId") Long userId);

    /**
     * Pause all ACTIVE roadmaps for a user (used when activating a new one)
     */
    @Modifying
    @Query("UPDATE RoadmapSession rs SET rs.status = 'PAUSED', rs.updatedAt = CURRENT_TIMESTAMP WHERE rs.user.id = :userId AND rs.status = 'ACTIVE'")
    int pauseAllActiveByUserId(@Param("userId") Long userId);
}
