package com.exe.skillverse_backend.course_service.repository;

import com.exe.skillverse_backend.course_service.entity.QuizQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, Long> {

    /**
     * Find questions by quiz ID ordered by order index ascending
     */
    @Transactional(readOnly = true)
    @Query("SELECT qq FROM QuizQuestion qq WHERE qq.quiz.id = :quizId ORDER BY qq.orderIndex ASC")
    List<QuizQuestion> findByQuizIdOrderByOrderIndexAsc(@Param("quizId") Long quizId);

    /**
     * Find question by ID and quiz ID
     */
    @Transactional(readOnly = true)
    @Query("SELECT qq FROM QuizQuestion qq WHERE qq.id = :id AND qq.quiz.id = :quizId")
    Optional<QuizQuestion> findByIdAndQuizId(@Param("id") Long id, @Param("quizId") Long quizId);

    /**
     * Delete all questions by quiz ID
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM QuizQuestion qq WHERE qq.quiz.id = :quizId")
    int deleteByQuizId(@Param("quizId") Long quizId);

    /**
     * Find questions by quiz ID
     */
    @Transactional(readOnly = true)
    @Query("SELECT qq FROM QuizQuestion qq WHERE qq.quiz.id = :quizId")
    List<QuizQuestion> findByQuizId(@Param("quizId") Long quizId);

    /**
     * Count questions in a quiz
     */
    @Transactional(readOnly = true)
    @Query("SELECT COUNT(qq) FROM QuizQuestion qq WHERE qq.quiz.id = :quizId")
    long countByQuizId(@Param("quizId") Long quizId);

    /**
     * Find questions with total score calculation
     */
    @Transactional(readOnly = true)
    @Query("SELECT SUM(qq.score) FROM QuizQuestion qq WHERE qq.quiz.id = :quizId")
    Optional<Integer> sumScoreByQuizId(@Param("quizId") Long quizId);

    /**
     * Find next question in order
     */
    @Transactional(readOnly = true)
    @Query("SELECT qq FROM QuizQuestion qq WHERE qq.quiz.id = :quizId AND qq.orderIndex > :currentIndex ORDER BY qq.orderIndex ASC")
    Optional<QuizQuestion> findNextQuestion(@Param("quizId") Long quizId, @Param("currentIndex") Integer currentIndex);
}
