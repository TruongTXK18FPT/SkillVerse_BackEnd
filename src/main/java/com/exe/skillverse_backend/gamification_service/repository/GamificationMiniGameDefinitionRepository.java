package com.exe.skillverse_backend.gamification_service.repository;

import com.exe.skillverse_backend.gamification_service.entity.GamificationMiniGameDefinition;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface GamificationMiniGameDefinitionRepository extends JpaRepository<GamificationMiniGameDefinition, Long> {
    
    Optional<GamificationMiniGameDefinition> findByGameKey(String gameKey);
    
    List<GamificationMiniGameDefinition> findByIsActiveTrue();
    
    List<GamificationMiniGameDefinition> findByIsActiveTrueAndIsPremiumOnlyFalse();
    
    List<GamificationMiniGameDefinition> findByGameType(String gameType);
    
    @Query("SELECT g FROM GamificationMiniGameDefinition g WHERE g.isActive = true AND " +
           "(g.isPremiumOnly = false OR (g.isPremiumOnly = true AND g.requiredPremiumPlan = :userPlan))")
    List<GamificationMiniGameDefinition> findAvailableGamesForUser(@Param("userPlan") String userPlan);
    
    boolean existsByGameKey(String gameKey);
}
