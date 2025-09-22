package com.exe.skillverse_backend.course_service.repository;

import com.exe.skillverse_backend.course_service.entity.QuizOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface QuizOptionRepository extends JpaRepository<QuizOption, Long> {

    /**
     * Find options by question ID ordered by natural order (no orderIndex in entity)
     */
    @Transactional(readOnly = true)
    @Query("SELECT qo FROM QuizOption qo WHERE qo.question.id = :questionId ORDER BY qo.id ASC")
    List<QuizOption> findByQuestionIdOrderById(@Param("questionId") Long questionId);

    /**
     * Delete all options by question ID
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM QuizOption qo WHERE qo.question.id = :questionId")
    int deleteByQuestionId(@Param("questionId") Long questionId);

    /**
     * Find options by question ID
     */
    @Transactional(readOnly = true)
    @Query("SELECT qo FROM QuizOption qo WHERE qo.question.id = :questionId")
    List<QuizOption> findByQuestionId(@Param("questionId") Long questionId);

    /**
     * Find correct options for a question
     */
    @Transactional(readOnly = true)
    @Query("SELECT qo FROM QuizOption qo WHERE qo.question.id = :questionId AND qo.isCorrect = true")
    List<QuizOption> findCorrectOptionsByQuestionId(@Param("questionId") Long questionId);

    /**
     * Count options for a question
     */
    @Transactional(readOnly = true)
    @Query("SELECT COUNT(qo) FROM QuizOption qo WHERE qo.question.id = :questionId")
    long countByQuestionId(@Param("questionId") Long questionId);

    /**
     * Count correct options for a question
     */
    @Transactional(readOnly = true)
    @Query("SELECT COUNT(qo) FROM QuizOption qo WHERE qo.question.id = :questionId AND qo.isCorrect = true")
    long countCorrectOptionsByQuestionId(@Param("questionId") Long questionId);

    /**
     * Check if question has correct options
     */
    @Transactional(readOnly = true)
    @Query("SELECT CASE WHEN COUNT(qo) > 0 THEN true ELSE false END " +
           "FROM QuizOption qo WHERE qo.question.id = :questionId AND qo.isCorrect = true")
    boolean hasCorrectOptions(@Param("questionId") Long questionId);
}
