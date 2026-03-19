package com.exe.skillverse_backend.gamification_service.repository;

import com.exe.skillverse_backend.gamification_service.entity.GamificationBadgeDefinition;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface GamificationBadgeDefinitionRepository extends JpaRepository<GamificationBadgeDefinition, Long> {
    
    Optional<GamificationBadgeDefinition> findByBadgeKey(String badgeKey);
    
    List<GamificationBadgeDefinition> findByIsActiveTrue();
    
    List<GamificationBadgeDefinition> findByBadgeCategory(String badgeCategory);
    
    List<GamificationBadgeDefinition> findByIsActiveTrueAndBadgeCategory(String badgeCategory);
    
    @Query("SELECT b FROM GamificationBadgeDefinition b WHERE b.isActive = true ORDER BY b.displayOrder ASC, b.badgeRarity DESC")
    List<GamificationBadgeDefinition> findAllActiveOrderedByDisplayAndRarity();
    
    @Query("SELECT COUNT(b) FROM GamificationBadgeDefinition b WHERE b.badgeCategory = :category AND b.isActive = true")
    Long countActiveBadgesByCategory(@Param("category") String category);
    
    boolean existsByBadgeKey(String badgeKey);
}
