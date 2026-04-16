package com.exe.skillverse_backend.question_bank_service.repository;

import com.exe.skillverse_backend.question_bank_service.entity.QuestionBank;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QuestionBankRepository extends JpaRepository<QuestionBank, Long> {

    Page<QuestionBank> findByIsActiveTrue(Pageable pageable);

    @Query("SELECT q FROM QuestionBank q WHERE q.isActive = true " +
           "AND (:domain IS NULL OR q.domain = :domain) " +
           "AND (:industry IS NULL OR q.industry = :industry) " +
           "AND (:jobRole IS NULL OR q.jobRole = :jobRole)")
    Page<QuestionBank> findByFilters(@Param("domain") String domain,
                                     @Param("industry") String industry,
                                     @Param("jobRole") String jobRole,
                                     Pageable pageable);

    Optional<QuestionBank> findTopByDomainAndIndustryAndJobRoleAndIsActiveTrueOrderByUpdatedAtDescIdDesc(
            String domain,
            String industry,
            String jobRole
    );

    /**
     * Find active question bank by domain + job role (no industry filter).
     * Used by JourneyService for bank-first test generation.
     */
    Optional<QuestionBank> findTopByDomainAndJobRoleAndIsActiveTrueOrderByUpdatedAtDescIdDesc(
            String domain,
            String jobRole
    );

    List<QuestionBank> findByDomainAndIsActiveTrue(String domain);

    boolean existsByDomainAndIndustryAndJobRoleAndIsActiveTrue(String domain, String industry, String jobRole);

    boolean existsByDomainAndIndustryAndJobRoleAndIsActiveTrueAndIdNot(
            String domain,
            String industry,
            String jobRole,
            Long id
    );
}
