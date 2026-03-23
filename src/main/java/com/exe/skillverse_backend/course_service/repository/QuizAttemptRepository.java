package com.exe.skillverse_backend.course_service.repository;

import com.exe.skillverse_backend.course_service.entity.QuizAttempt;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {

    List<QuizAttempt> findByQuizIdAndUserIdOrderBySubmittedAtDesc(Long quizId, Long userId);

    List<QuizAttempt> findByQuizIdInAndUserIdOrderBySubmittedAtDesc(List<Long> quizIds, Long userId);

    Long countByQuizIdAndUserIdAndSubmittedAtAfter(Long quizId, Long userId, Instant after);

    Long countByQuizIdAndUserId(Long quizId, Long userId);

    @Query("SELECT DISTINCT qa.quiz.id FROM QuizAttempt qa " +
            "WHERE qa.userId = :userId " +
            "AND qa.quiz.module.course.id = :courseId " +
            "AND qa.passed = true")
    List<Long> findPassedQuizIdsByCourseAndUser(@Param("courseId") Long courseId,
                                                @Param("userId") Long userId);

    @Query("SELECT qa.quiz.id AS quizId, qa.quiz.title AS quizTitle, qa.score AS score, qa.submittedAt AS submittedAt " +
            "FROM QuizAttempt qa " +
            "WHERE qa.userId = :userId " +
            "AND qa.quiz.module.course.id = :courseId " +
            "AND qa.passed = true " +
            "AND NOT EXISTS (" +
            "  SELECT 1 FROM QuizAttempt newer " +
            "  WHERE newer.userId = qa.userId " +
            "    AND newer.quiz.id = qa.quiz.id " +
            "    AND newer.passed = true " +
            "    AND newer.quiz.module.course.id = qa.quiz.module.course.id " +
            "    AND (newer.submittedAt > qa.submittedAt " +
            "      OR (newer.submittedAt = qa.submittedAt AND newer.id > qa.id))" +
            ") " +
            "ORDER BY qa.submittedAt DESC, qa.id DESC")
    List<PassedQuizAttemptSummary> findPassedQuizAttemptSummariesByCourseAndUser(
            @Param("courseId") Long courseId,
            @Param("userId") Long userId
    );

    @Query("SELECT qa FROM QuizAttempt qa " +
            "WHERE qa.userId = :userId " +
            "AND qa.quiz.module.course.id = :courseId " +
            "AND qa.passed = true " +
            "AND NOT EXISTS (" +
            "  SELECT 1 FROM QuizAttempt newer " +
            "  WHERE newer.userId = qa.userId " +
            "    AND newer.quiz.id = qa.quiz.id " +
            "    AND newer.passed = true " +
            "    AND newer.quiz.module.course.id = qa.quiz.module.course.id " +
            "    AND (newer.submittedAt > qa.submittedAt " +
            "      OR (newer.submittedAt = qa.submittedAt AND newer.id > qa.id))" +
            ") " +
            "ORDER BY qa.submittedAt DESC, qa.id DESC")
    List<QuizAttempt> findPassedQuizAttemptsByCourseAndUser(
            @Param("courseId") Long courseId,
            @Param("userId") Long userId
    );

    interface PassedQuizAttemptSummary {
        Long getQuizId();
        String getQuizTitle();
        Integer getScore();
        Instant getSubmittedAt();
    }
}
