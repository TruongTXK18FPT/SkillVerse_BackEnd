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
           "(:domainPattern IS NULL OR e.domain LIKE :domainPattern) AND " +
           "(:industryPattern IS NULL OR e.industry LIKE :industryPattern) AND " +
           "(e.jobRole LIKE :rolePattern)")
    List<ExpertPromptConfig> findMatchingPrompts(@Param("domainPattern") String domainPattern, 
                                                @Param("industryPattern") String industryPattern, 
                                                @Param("rolePattern") String rolePattern);

    boolean existsByJobRole(String jobRole);
}
