package com.exe.skillverse_backend.gamification_service.repository;

import com.exe.skillverse_backend.gamification_service.entity.GamificationActivityLog;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface GamificationActivityLogRepository extends JpaRepository<GamificationActivityLog, Long> {
    
    Page<GamificationActivityLog> findByUserIdOrderByActivityTimestampDesc(Long userId, Pageable pageable);
    
    List<GamificationActivityLog> findByUserIdAndActivityType(Long userId, String activityType);
    
    @Query("SELECT a FROM GamificationActivityLog a WHERE a.userId = :userId AND a.isVerified = true " +
           "AND a.activityTimestamp >= :since ORDER BY a.activityTimestamp DESC")
    List<GamificationActivityLog> findVerifiedActivitiesSince(
        @Param("userId") Long userId, 
        @Param("since") LocalDateTime since);
    
    @Query("SELECT COUNT(a) FROM GamificationActivityLog a WHERE a.userId = :userId " +
           "AND a.activityType = :activityType AND a.isVerified = true " +
           "AND a.activityTimestamp >= :since")
    Long countVerifiedActivitiesByTypeSince(
        @Param("userId") Long userId, 
        @Param("activityType") String activityType, 
        @Param("since") LocalDateTime since);
    
    @Query("SELECT a.userId, COUNT(a) FROM GamificationActivityLog a WHERE a.activityType = :activityType " +
           "AND a.isVerified = true AND a.activityTimestamp >= :since GROUP BY a.userId")
    List<Object[]> countVerifiedActivitiesByTypeSinceGroupedByUserId(
        @Param("activityType") String activityType, 
        @Param("since") LocalDateTime since);

    @Query("SELECT COALESCE(SUM(a.durationMinutes), 0) FROM GamificationActivityLog a " +
           "WHERE a.userId = :userId AND a.activityType = 'STUDY' AND a.isVerified = true " +
           "AND a.activityTimestamp >= :since")
    Integer sumStudyMinutesSince(@Param("userId") Long userId, @Param("since") LocalDateTime since);
}
