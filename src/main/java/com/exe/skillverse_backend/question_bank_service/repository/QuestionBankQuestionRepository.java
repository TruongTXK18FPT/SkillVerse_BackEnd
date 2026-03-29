package com.exe.skillverse_backend.question_bank_service.repository;

import com.exe.skillverse_backend.question_bank_service.entity.QuestionBankQuestion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuestionBankQuestionRepository extends JpaRepository<QuestionBankQuestion, Long> {

    Page<QuestionBankQuestion> findByQuestionBankIdAndIsActiveTrue(Long bankId, Pageable pageable);

    @Query("SELECT q FROM QuestionBankQuestion q WHERE q.questionBank.id = :bankId " +
           "AND q.isActive = true " +
           "AND (:difficulty IS NULL OR q.difficulty = :difficulty) " +
           "AND (:skillArea IS NULL OR q.skillArea = :skillArea) " +
           "AND (:category IS NULL OR q.category = :category)")
    Page<QuestionBankQuestion> findByFilters(@Param("bankId") Long bankId,
                                             @Param("difficulty") String difficulty,
                                             @Param("skillArea") String skillArea,
                                             @Param("category") String category,
                                             Pageable pageable);

    int countByQuestionBankIdAndIsActive(Long bankId, Boolean isActive);

    long countByQuestionBankId(Long bankId);

    @Query(value = "SELECT * FROM question_bank_questions " +
            "WHERE question_bank_id = :bankId AND is_active = true AND difficulty = :difficulty " +
            "ORDER BY RANDOM() LIMIT :limit",
            nativeQuery = true)
    List<QuestionBankQuestion> findRandomActiveByBankAndDifficulty(
            @Param("bankId") Long bankId,
            @Param("difficulty") String difficulty,
            @Param("limit") int limit);

    @Query(value = "SELECT * FROM question_bank_questions " +
            "WHERE question_bank_id = :bankId AND is_active = true AND id NOT IN (:excludeIds) " +
            "ORDER BY RANDOM() LIMIT :limit",
            nativeQuery = true)
    List<QuestionBankQuestion> findRandomActiveByBankExcluding(
            @Param("bankId") Long bankId,
            @Param("excludeIds") List<Long> excludeIds,
            @Param("limit") int limit);

    @Modifying
    @Query("UPDATE QuestionBankQuestion q SET q.usedCount = q.usedCount + 1 WHERE q.id IN :ids")
    void incrementUsedCount(@Param("ids") List<Long> ids);

    @Query("SELECT q.difficulty, COUNT(q) FROM QuestionBankQuestion q " +
           "WHERE q.questionBank.id = :bankId AND q.isActive = true " +
           "GROUP BY q.difficulty")
    List<Object[]> countByDifficulty(@Param("bankId") Long bankId);

    @Query("SELECT DISTINCT q.skillArea FROM QuestionBankQuestion q " +
           "WHERE q.questionBank.id = :bankId AND q.isActive = true AND q.skillArea IS NOT NULL")
    List<String> findDistinctSkillAreas(@Param("bankId") Long bankId);
}
