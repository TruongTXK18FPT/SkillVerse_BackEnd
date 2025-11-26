package com.exe.skillverse_backend.course_service.repository;

import com.exe.skillverse_backend.course_service.entity.QuizAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {

    List<QuizAttempt> findByQuizIdAndUserIdOrderBySubmittedAtDesc(Long quizId, Long userId);

    Long countByQuizIdAndUserIdAndSubmittedAtAfter(Long quizId, Long userId, Instant after);
}
