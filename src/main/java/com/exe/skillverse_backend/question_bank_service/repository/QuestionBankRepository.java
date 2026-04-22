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
           "AND (:jobRole IS NULL OR q.jobRole = :jobRole) " +
           "AND (:skillName IS NULL OR q.skillName = :skillName)")
    Page<QuestionBank> findByFilters(@Param("domain") String domain,
                                     @Param("industry") String industry,
                                     @Param("jobRole") String jobRole,
                                     @Param("skillName") String skillName,
                                     Pageable pageable);

    @Query("""
            SELECT q FROM QuestionBank q
            WHERE q.isActive = true
              AND q.domain = :domain
              AND ((:industry IS NULL AND q.industry IS NULL) OR q.industry = :industry)
              AND ((:jobRole IS NULL AND q.jobRole IS NULL) OR q.jobRole = :jobRole)
              AND ((:skillName IS NULL AND q.skillName IS NULL) OR q.skillName = :skillName)
            ORDER BY q.updatedAt DESC, q.id DESC
            """)
    List<QuestionBank> findByExactScope(@Param("domain") String domain,
                                        @Param("industry") String industry,
                                        @Param("jobRole") String jobRole,
                                        @Param("skillName") String skillName,
                                        Pageable pageable);

    @Query("""
            SELECT q FROM QuestionBank q
            WHERE q.isActive = true
              AND q.domain = :domain
              AND ((:industry IS NULL AND q.industry IS NULL) OR q.industry = :industry)
              AND ((:jobRole IS NULL AND q.jobRole IS NULL) OR q.jobRole = :jobRole)
            ORDER BY CASE WHEN q.skillName IS NULL THEN 0 ELSE 1 END, q.updatedAt DESC, q.id DESC
            """)
    List<QuestionBank> findPreferredByScope(@Param("domain") String domain,
                                            @Param("industry") String industry,
                                            @Param("jobRole") String jobRole,
                                            Pageable pageable);

    @Query("""
            SELECT q FROM QuestionBank q
            WHERE q.isActive = true
              AND q.domain = :domain
              AND ((:jobRole IS NULL AND q.jobRole IS NULL) OR q.jobRole = :jobRole)
              AND ((:skillName IS NULL AND q.skillName IS NULL) OR q.skillName = :skillName)
            ORDER BY q.updatedAt DESC, q.id DESC
            """)
    List<QuestionBank> findByExactDomainAndRole(@Param("domain") String domain,
                                                @Param("jobRole") String jobRole,
                                                @Param("skillName") String skillName,
                                                Pageable pageable);

    @Query("""
            SELECT q FROM QuestionBank q
            WHERE q.isActive = true
              AND q.domain = :domain
              AND ((:jobRole IS NULL AND q.jobRole IS NULL) OR q.jobRole = :jobRole)
            ORDER BY CASE WHEN q.skillName IS NULL THEN 0 ELSE 1 END, q.updatedAt DESC, q.id DESC
            """)
    List<QuestionBank> findPreferredByDomainAndRole(@Param("domain") String domain,
                                                    @Param("jobRole") String jobRole,
                                                    Pageable pageable);

    List<QuestionBank> findByDomainAndIsActiveTrue(String domain);

    @Query("""
            SELECT CASE WHEN COUNT(q) > 0 THEN TRUE ELSE FALSE END FROM QuestionBank q
            WHERE q.isActive = true
              AND q.domain = :domain
              AND ((:industry IS NULL AND q.industry IS NULL) OR q.industry = :industry)
              AND ((:jobRole IS NULL AND q.jobRole IS NULL) OR q.jobRole = :jobRole)
              AND ((:skillName IS NULL AND q.skillName IS NULL) OR q.skillName = :skillName)
            """)
    boolean existsActiveByScope(@Param("domain") String domain,
                                @Param("industry") String industry,
                                @Param("jobRole") String jobRole,
                                @Param("skillName") String skillName);

    @Query("""
            SELECT CASE WHEN COUNT(q) > 0 THEN TRUE ELSE FALSE END FROM QuestionBank q
            WHERE q.isActive = true
              AND q.domain = :domain
              AND ((:industry IS NULL AND q.industry IS NULL) OR q.industry = :industry)
              AND ((:jobRole IS NULL AND q.jobRole IS NULL) OR q.jobRole = :jobRole)
              AND ((:skillName IS NULL AND q.skillName IS NULL) OR q.skillName = :skillName)
              AND q.id <> :excludeId
            """)
    boolean existsActiveByScopeAndIdNot(@Param("domain") String domain,
                                        @Param("industry") String industry,
                                        @Param("jobRole") String jobRole,
                                        @Param("skillName") String skillName,
                                        @Param("excludeId") Long excludeId);
}
