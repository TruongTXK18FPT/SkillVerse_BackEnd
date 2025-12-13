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
    List<Object[]> countGroupedByModeInRange(@Param("from") java.time.Instant from, @Param("to") java.time.Instant to);

    @Query("SELECT rs.roadmapMode, COUNT(rs) FROM RoadmapSession rs WHERE rs.user.id = :userId AND rs.createdAt BETWEEN :from AND :to GROUP BY rs.roadmapMode")
    List<Object[]> countGroupedByModeInRangeForUser(@Param("userId") Long userId, @Param("from") java.time.Instant from, @Param("to") java.time.Instant to);

    @Query(value = "SELECT date_trunc('day', created_at AT TIME ZONE 'Asia/Ho_Chi_Minh') AS bucket, roadmap_mode, COUNT(*) AS cnt " +
            "FROM roadmap_sessions WHERE created_at BETWEEN :from AND :to " +
            "GROUP BY bucket, roadmap_mode ORDER BY bucket", nativeQuery = true)
    List<Object[]> countModeDaily(@Param("from") java.time.Instant from, @Param("to") java.time.Instant to);

    @Query(value = "SELECT date_trunc('day', created_at AT TIME ZONE 'Asia/Ho_Chi_Minh') AS bucket, roadmap_mode, COUNT(*) AS cnt " +
            "FROM roadmap_sessions WHERE user_id = :userId AND created_at BETWEEN :from AND :to " +
            "GROUP BY bucket, roadmap_mode ORDER BY bucket", nativeQuery = true)
    List<Object[]> countModeDailyForUser(@Param("userId") Long userId, @Param("from") java.time.Instant from, @Param("to") java.time.Instant to);

    @Query(value = "SELECT date_trunc('week', created_at AT TIME ZONE 'Asia/Ho_Chi_Minh') AS bucket, roadmap_mode, COUNT(*) AS cnt " +
            "FROM roadmap_sessions WHERE created_at BETWEEN :from AND :to " +
            "GROUP BY bucket, roadmap_mode ORDER BY bucket", nativeQuery = true)
    List<Object[]> countModeWeekly(@Param("from") java.time.Instant from, @Param("to") java.time.Instant to);

    @Query(value = "SELECT date_trunc('week', created_at AT TIME ZONE 'Asia/Ho_Chi_Minh') AS bucket, roadmap_mode, COUNT(*) AS cnt " +
            "FROM roadmap_sessions WHERE user_id = :userId AND created_at BETWEEN :from AND :to " +
            "GROUP BY bucket, roadmap_mode ORDER BY bucket", nativeQuery = true)
    List<Object[]> countModeWeeklyForUser(@Param("userId") Long userId, @Param("from") java.time.Instant from, @Param("to") java.time.Instant to);

    @Query(value = "SELECT date_trunc('month', created_at AT TIME ZONE 'Asia/Ho_Chi_Minh') AS bucket, roadmap_mode, COUNT(*) AS cnt " +
            "FROM roadmap_sessions WHERE created_at BETWEEN :from AND :to " +
            "GROUP BY bucket, roadmap_mode ORDER BY bucket", nativeQuery = true)
    List<Object[]> countModeMonthly(@Param("from") java.time.Instant from, @Param("to") java.time.Instant to);

    @Query(value = "SELECT date_trunc('month', created_at AT TIME ZONE 'Asia/Ho_Chi_Minh') AS bucket, roadmap_mode, COUNT(*) AS cnt " +
            "FROM roadmap_sessions WHERE user_id = :userId AND created_at BETWEEN :from AND :to " +
            "GROUP BY bucket, roadmap_mode ORDER BY bucket", nativeQuery = true)
    List<Object[]> countModeMonthlyForUser(@Param("userId") Long userId, @Param("from") java.time.Instant from, @Param("to") java.time.Instant to);
}
