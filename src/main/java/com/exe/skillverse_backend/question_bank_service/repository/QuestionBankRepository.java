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

    @Query("""
           SELECT q FROM QuestionBank q WHERE q.isActive = true
           AND (:domainId IS NULL OR q.domainId = :domainId)
           AND (:jobPositionId IS NULL OR q.jobPositionId = :jobPositionId)
           AND (:skillId IS NULL OR q.skillId = :skillId)
           """)
    Page<QuestionBank> findByTaxonomyFilters(@Param("domainId") Long domainId,
                                             @Param("jobPositionId") Long jobPositionId,
                                             @Param("skillId") Long skillId,
                                             Pageable pageable);

    @Query("""
            SELECT q FROM QuestionBank q
            WHERE q.isActive = true
              AND q.domainId = :domainId
              AND q.jobPositionId = :jobPositionId
              AND ((:skillId IS NULL AND q.skillId IS NULL) OR q.skillId = :skillId)
            ORDER BY q.updatedAt DESC, q.id DESC
            """)
    List<QuestionBank> findByExactTaxonomyScope(@Param("domainId") Long domainId,
                                                @Param("jobPositionId") Long jobPositionId,
                                                @Param("skillId") Long skillId,
                                                Pageable pageable);

    @Query("""
            SELECT q FROM QuestionBank q
            WHERE q.isActive = true
              AND q.domainId = :domainId
              AND q.jobPositionId = :jobPositionId
            ORDER BY CASE WHEN q.skillId IS NULL THEN 0 ELSE 1 END, q.updatedAt DESC, q.id DESC
            """)
    List<QuestionBank> findPreferredByTaxonomyScope(@Param("domainId") Long domainId,
                                                    @Param("jobPositionId") Long jobPositionId,
                                                    Pageable pageable);

    @Query("""
            SELECT CASE WHEN COUNT(q) > 0 THEN TRUE ELSE FALSE END FROM QuestionBank q
            WHERE q.isActive = true
              AND q.domainId = :domainId
              AND q.jobPositionId = :jobPositionId
              AND ((:skillId IS NULL AND q.skillId IS NULL) OR q.skillId = :skillId)
            """)
    boolean existsActiveByTaxonomyScope(@Param("domainId") Long domainId,
                                        @Param("jobPositionId") Long jobPositionId,
                                        @Param("skillId") Long skillId);

    @Query("""
            SELECT CASE WHEN COUNT(q) > 0 THEN TRUE ELSE FALSE END FROM QuestionBank q
            WHERE q.isActive = true
              AND q.domainId = :domainId
              AND q.jobPositionId = :jobPositionId
              AND ((:skillId IS NULL AND q.skillId IS NULL) OR q.skillId = :skillId)
              AND q.id <> :excludeId
            """)
    boolean existsActiveByTaxonomyScopeAndIdNot(@Param("domainId") Long domainId,
                                                @Param("jobPositionId") Long jobPositionId,
                                                @Param("skillId") Long skillId,
                                                @Param("excludeId") Long excludeId);

    List<QuestionBank> findByDomainAndIsActiveTrue(String domain);
}
