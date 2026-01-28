package com.exe.skillverse_backend.gamification_service.repository;

import com.exe.skillverse_backend.gamification_service.entity.GamificationUserBadge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface GamificationUserBadgeRepository extends JpaRepository<GamificationUserBadge, Long> {
    
    List<GamificationUserBadge> findByUserIdOrderByEarnedAtDesc(Long userId);
    
    List<GamificationUserBadge> findByUserId(Long userId);
    
    Optional<GamificationUserBadge> findByUserIdAndBadgeDefId(Long userId, Long badgeDefId);
    
    boolean existsByUserIdAndBadgeDefId(Long userId, Long badgeDefId);
    
    @Query("SELECT COUNT(ub) FROM GamificationUserBadge ub WHERE ub.userId = :userId")
    Long countBadgesByUserId(@Param("userId") Long userId);
    
    Long countByUserId(Long userId);
    
    @Query("SELECT ub FROM GamificationUserBadge ub " +
           "JOIN FETCH ub.badgeDefinition bd " +
           "WHERE ub.userId = :userId AND bd.badgeCategory = :category " +
           "ORDER BY ub.earnedAt DESC")
    List<GamificationUserBadge> findByUserIdAndBadgeCategory(
        @Param("userId") Long userId, 
        @Param("category") String category);
    
    @Query("SELECT ub FROM GamificationUserBadge ub " +
           "WHERE ub.userId = :userId AND ub.earnedAt >= :since " +
           "ORDER BY ub.earnedAt DESC")
    List<GamificationUserBadge> findRecentBadgesByUserId(
        @Param("userId") Long userId, 
        @Param("since") LocalDateTime since);
    
    @Query("SELECT COALESCE(SUM(ub.coinsAwarded), 0) FROM GamificationUserBadge ub WHERE ub.userId = :userId")
    Integer sumCoinsFromBadgesByUserId(@Param("userId") Long userId);

    // Admin dashboard queries
    Long countByEarnedAtAfter(LocalDateTime date);

    Long countByEarnedAtBetween(LocalDateTime start, LocalDateTime end);

    @Query("SELECT COUNT(ub) FROM GamificationUserBadge ub WHERE ub.badgeDefinition.badgeDefId = :badgeDefId")
    Long countByBadgeDefId(@Param("badgeDefId") Long badgeDefId);

    @Query("SELECT COUNT(ub) FROM GamificationUserBadge ub WHERE ub.badgeDefinition.badgeDefId = :badgeDefId AND ub.earnedAt > :date")
    Long countByBadgeDefinitionIdAndEarnedAtAfter(@Param("badgeDefId") Long badgeDefId, @Param("date") LocalDateTime date);
}
