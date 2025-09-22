package com.exe.skillverse_backend.course_service.repository;

import com.exe.skillverse_backend.course_service.entity.Quiz;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface QuizRepository extends JpaRepository<Quiz, Long> {

    /**
     * Find quiz by ID and lesson ID
     */
    @Transactional(readOnly = true)
    @Query("SELECT q FROM Quiz q WHERE q.id = :quizId AND q.lesson.id = :lessonId")
    Optional<Quiz> findByIdAndLessonId(@Param("quizId") Long quizId, @Param("lessonId") Long lessonId);

    /**
     * Find quizzes by lesson ID ordered by creation time
     */
    @Transactional(readOnly = true)
    @Query("SELECT q FROM Quiz q WHERE q.lesson.id = :lessonId ORDER BY q.createdAt ASC")
    List<Quiz> findByLessonIdOrderByCreatedAtAsc(@Param("lessonId") Long lessonId);

    /**
     * Find quiz by lesson ID (assuming one quiz per lesson)
     */
    @Transactional(readOnly = true)
    @Query("SELECT q FROM Quiz q WHERE q.lesson.id = :lessonId")
    Optional<Quiz> findByLessonId(@Param("lessonId") Long lessonId);

    /**
     * Find quizzes by course ID
     */
    @Transactional(readOnly = true)
    @Query("SELECT q FROM Quiz q WHERE q.lesson.course.id = :courseId")
    List<Quiz> findByCourseId(@Param("courseId") Long courseId);

    /**
     * Count quizzes in a course
     */
    @Transactional(readOnly = true)
    @Query("SELECT COUNT(q) FROM Quiz q WHERE q.lesson.course.id = :courseId")
    long countByCourseId(@Param("courseId") Long courseId);

    /**
     * Check if quiz exists for lesson
     */
    @Transactional(readOnly = true)
    @Query("SELECT CASE WHEN COUNT(q) > 0 THEN true ELSE false END FROM Quiz q WHERE q.lesson.id = :lessonId")
    boolean existsByLessonId(@Param("lessonId") Long lessonId);
}
