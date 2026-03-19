package com.exe.skillverse_backend.course_service.repository;

import com.exe.skillverse_backend.course_service.entity.QuizAttemptAnswerSnapshot;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface QuizAttemptAnswerSnapshotRepository extends JpaRepository<QuizAttemptAnswerSnapshot, Long> {

    List<QuizAttemptAnswerSnapshot> findByAttemptIdOrderByQuestionOrderIndexAscIdAsc(Long attemptId);
}
