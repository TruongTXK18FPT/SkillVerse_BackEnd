package com.exe.skillverse_backend.course_service.repository;

import com.exe.skillverse_backend.course_service.entity.Lesson;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface LessonRepository extends JpaRepository<Lesson, Long> {

    /**
     * Find lessons by module ID ordered by order index ascending
     */
    @Transactional(readOnly = true)
    List<Lesson> findByModuleIdOrderByOrderIndexAsc(Long moduleId);

    /**
     * Find lesson by ID and module ID
     */
    @Transactional(readOnly = true)
    Optional<Lesson> findByIdAndModuleId(Long lessonId, Long moduleId);

    /**
     * Check if lesson with given title exists in module (case insensitive)
     */
    @Transactional(readOnly = true)
    boolean existsByModuleIdAndTitleIgnoreCase(Long moduleId, String title);

    /**
     * Delete all lessons by module ID
     */
    @Modifying
    @Transactional
    int deleteByModuleId(Long moduleId);

    /**
     * Find lessons by module ID
     */
    @Transactional(readOnly = true)
    List<Lesson> findByModuleId(Long moduleId);

    /**
     * Count lessons in a module
     */
    @Transactional(readOnly = true)
    long countByModuleId(Long moduleId);

    /**
     * Count lessons in a course (across all modules)
     */
    @Transactional(readOnly = true)
    @Query("SELECT COUNT(l) FROM Lesson l WHERE l.module.course.id = :courseId")
    long countByCourseId(@Param("courseId") Long courseId);

    /**
     * Find next lesson by module and order index
     */
    @Transactional(readOnly = true)
    @Query("SELECT l FROM Lesson l WHERE l.module.id = :moduleId AND l.orderIndex > :currentIndex ORDER BY l.orderIndex ASC")
    Optional<Lesson> findNextLesson(@Param("moduleId") Long moduleId, @Param("currentIndex") Integer currentIndex);

    /**
     * Find previous lesson by module and order index
     */
    @Transactional(readOnly = true)
    @Query("SELECT l FROM Lesson l WHERE l.module.id = :moduleId AND l.orderIndex < :currentIndex ORDER BY l.orderIndex DESC")
    Optional<Lesson> findPreviousLesson(@Param("moduleId") Long moduleId, @Param("currentIndex") Integer currentIndex);
}
