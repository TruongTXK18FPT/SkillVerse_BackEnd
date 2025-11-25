package com.exe.skillverse_backend.ai_service.repository;

import com.exe.skillverse_backend.ai_service.entity.ExpertPromptConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExpertPromptConfigRepository extends JpaRepository<ExpertPromptConfig, Long> {
    
    // Find active config matching the exact hierarchy
    Optional<ExpertPromptConfig> findByDomainAndIndustryAndJobRoleAndIsActiveTrue(String domain, String industry, String jobRole);
    
    // Fuzzy search for matching roles using keyword
    @Query("SELECT e FROM ExpertPromptConfig e WHERE e.isActive = true AND " +
           "(:domain IS NULL OR LOWER(e.domain) LIKE LOWER(CONCAT('%', :domain, '%'))) AND " +
           "(:industry IS NULL OR LOWER(e.industry) LIKE LOWER(CONCAT('%', :industry, '%'))) AND " +
           "(LOWER(e.jobRole) LIKE LOWER(CONCAT('%', :role, '%')) OR " +
           "LOWER(e.keywords) LIKE LOWER(CONCAT('%', :role, '%')))")
    List<ExpertPromptConfig> findMatchingPrompts(@Param("domain") String domain, 
                                                @Param("industry") String industry, 
                                                @Param("role") String role);

    boolean existsByJobRole(String jobRole);
}
