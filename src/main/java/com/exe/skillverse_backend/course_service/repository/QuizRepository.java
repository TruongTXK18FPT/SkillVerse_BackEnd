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
     * Find quiz by ID and module ID
     */
    @Transactional(readOnly = true)
    @Query("SELECT q FROM Quiz q WHERE q.id = :quizId AND q.module.id = :moduleId")
    Optional<Quiz> findByIdAndModuleId(@Param("quizId") Long quizId, @Param("moduleId") Long moduleId);

    /**
     * Find quizzes by module ID ordered by creation time
     */
    @Transactional(readOnly = true)
    @Query("SELECT q FROM Quiz q WHERE q.module.id = :moduleId ORDER BY q.createdAt ASC")
    List<Quiz> findByModuleIdOrderByCreatedAtAsc(@Param("moduleId") Long moduleId);

    /**
     * Find quizzes by module ID with questions eagerly loaded for summary
     */
    @Transactional(readOnly = true)
    @Query("SELECT q FROM Quiz q LEFT JOIN FETCH q.questions WHERE q.module.id = :moduleId ORDER BY q.createdAt ASC")
    List<Quiz> findByModuleIdWithQuestions(@Param("moduleId") Long moduleId);

    /**
     * Find quiz by module ID (assuming one quiz per module)
     */
    @Transactional(readOnly = true)
    @Query("SELECT q FROM Quiz q WHERE q.module.id = :moduleId")
    Optional<Quiz> findByModuleId(@Param("moduleId") Long moduleId);

    /**
     * Find quizzes by course ID
     */
    @Transactional(readOnly = true)
    @Query("SELECT q FROM Quiz q WHERE q.module.course.id = :courseId")
    List<Quiz> findByCourseId(@Param("courseId") Long courseId);

    /**
     * Count quizzes in a course
     */
    @Transactional(readOnly = true)
    @Query("SELECT COUNT(q) FROM Quiz q WHERE q.module.course.id = :courseId")
    long countByCourseId(@Param("courseId") Long courseId);

    /**
     * Check if quiz exists for module
     */
    @Transactional(readOnly = true)
    @Query("SELECT CASE WHEN COUNT(q) > 0 THEN true ELSE false END FROM Quiz q WHERE q.module.id = :moduleId")
    boolean existsByModuleId(@Param("moduleId") Long moduleId);
}
