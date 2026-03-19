package com.exe.skillverse_backend.gamification_service.repository;

import com.exe.skillverse_backend.gamification_service.entity.GamificationGameSession;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface GamificationGameSessionRepository extends JpaRepository<GamificationGameSession, Long> {
    
    Page<GamificationGameSession> findByUserIdOrderByPlayedAtDesc(Long userId, Pageable pageable);
    
    List<GamificationGameSession> findByUserIdAndGameDefIdOrderByPlayedAtDesc(Long userId, Long gameDefId);
    
    List<GamificationGameSession> findTop10ByUserIdOrderByPlayedAtDesc(Long userId);
    
    @Query("SELECT s FROM GamificationGameSession s WHERE s.userId = :userId AND s.gameDefId = :gameDefId " +
           "ORDER BY s.playedAt DESC LIMIT 1")
    Optional<GamificationGameSession> findLastSessionByUserAndGame(
        @Param("userId") Long userId, 
        @Param("gameDefId") Long gameDefId);
    
    @Query("SELECT COUNT(s) FROM GamificationGameSession s WHERE s.userId = :userId AND s.gameDefId = :gameDefId " +
           "AND s.playedAt >= :since AND s.sessionStatus = 'COMPLETED'")
    Long countCompletedSessionsSince(
        @Param("userId") Long userId, 
        @Param("gameDefId") Long gameDefId, 
        @Param("since") LocalDateTime since);
    
    @Query("SELECT COALESCE(SUM(s.coinsEarned), 0) FROM GamificationGameSession s " +
           "WHERE s.userId = :userId AND s.gameDefId = :gameDefId AND s.playedAt >= :since " +
           "AND s.sessionStatus = 'COMPLETED' AND s.isVerified = true")
    Integer sumCoinsEarnedFromGameSince(
        @Param("userId") Long userId, 
        @Param("gameDefId") Long gameDefId, 
        @Param("since") LocalDateTime since);
    
    @Query("SELECT s FROM GamificationGameSession s WHERE s.userId = :userId AND s.sessionStatus = 'IN_PROGRESS'")
    List<GamificationGameSession> findInProgressSessionsByUser(@Param("userId") Long userId);

    // Admin dashboard queries
    Long countByPlayedAtAfter(LocalDateTime date);

    Long countByPlayedAtBetween(LocalDateTime start, LocalDateTime end);

    Long countByUserId(Long userId);

    Long countByUserIdAndSessionStatus(Long userId, String status);

    @Query("SELECT COUNT(s) FROM GamificationGameSession s WHERE s.gameDefinition.gameDefId = :gameDefId")
    Long countByGameDefId(@Param("gameDefId") Long gameDefId);

    @Query("SELECT COUNT(s) FROM GamificationGameSession s WHERE s.gameDefinition.gameDefId = :gameDefId AND s.sessionStatus = :status")
    Long countByGameDefIdAndStatus(@Param("gameDefId") Long gameDefId, @Param("status") String status);

    @Query("SELECT COALESCE(SUM(s.coinsEarned), 0) FROM GamificationGameSession s WHERE s.gameDefinition.gameDefId = :gameDefId")
    Long sumCoinsEarnedByGameDefinitionId(@Param("gameDefId") Long gameDefId);

    @Query("SELECT COALESCE(SUM(s.xpEarned), 0) FROM GamificationGameSession s WHERE s.gameDefinition.gameDefId = :gameDefId")
    Long sumXpEarnedByGameDefinitionId(@Param("gameDefId") Long gameDefId);

    @Query("SELECT AVG(s.scoreAchieved) FROM GamificationGameSession s WHERE s.gameDefinition.gameDefId = :gameDefId AND s.scoreAchieved IS NOT NULL")
    Double avgScoreByGameDefinitionId(@Param("gameDefId") Long gameDefId);

    @Query("SELECT COUNT(DISTINCT s.userId) FROM GamificationGameSession s WHERE s.gameDefinition.gameDefId = :gameDefId")
    Long countDistinctUsersByGameDefinitionId(@Param("gameDefId") Long gameDefId);

    @Query("SELECT s.userId, COUNT(s) as sessionCount FROM GamificationGameSession s GROUP BY s.userId ORDER BY sessionCount DESC")
    List<Object[]> findMostActiveUsers(int limit);

    @Query(value = "SELECT s.user_id, COUNT(*) as session_count FROM gamification_game_sessions s GROUP BY s.user_id ORDER BY session_count DESC LIMIT :limit", nativeQuery = true)
    List<Object[]> findMostActiveUsersNative(@Param("limit") int limit);
}
