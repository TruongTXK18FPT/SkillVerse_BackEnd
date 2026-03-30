package com.exe.skillverse_backend.ai_service.repository;

import com.exe.skillverse_backend.ai_service.entity.TaxonomyEntry;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TaxonomyEntryRepository extends JpaRepository<TaxonomyEntry, Long> {
    List<TaxonomyEntry> findByActiveTrue();

    @Query("SELECT t FROM TaxonomyEntry t WHERE t.active = true AND (:domain IS NULL OR t.domain = :domain) AND (:industry IS NULL OR t.industry = :industry)")
    List<TaxonomyEntry> findActiveByDomainAndIndustry(String domain, String industry);

    Optional<TaxonomyEntry> findFirstByActiveTrueAndDomainIgnoreCaseAndIndustryIgnoreCaseAndRoleIgnoreCase(
            String domain,
            String industry,
            String role);

    Optional<TaxonomyEntry> findFirstByActiveTrueAndDomainIgnoreCaseAndRoleIgnoreCase(
            String domain,
            String role);

    Optional<TaxonomyEntry> findFirstByActiveTrueAndRoleIgnoreCase(String role);
}
