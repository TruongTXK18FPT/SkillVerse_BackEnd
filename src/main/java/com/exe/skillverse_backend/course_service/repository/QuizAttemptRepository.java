package com.exe.skillverse_backend.course_service.repository;

import com.exe.skillverse_backend.course_service.entity.QuizAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

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
}
