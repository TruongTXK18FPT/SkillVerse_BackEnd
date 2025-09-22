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
     * Find lessons by course ID ordered by order index ascending
     */
    @Transactional(readOnly = true)
    List<Lesson> findByCourseIdOrderByOrderIndexAsc(Long courseId);

    /**
     * Find lesson by ID and course ID
     */
    @Transactional(readOnly = true)
    Optional<Lesson> findByIdAndCourseId(Long lessonId, Long courseId);

    /**
     * Check if lesson with given title exists in course (case insensitive)
     */
    @Transactional(readOnly = true)
    boolean existsByCourseIdAndTitleIgnoreCase(Long courseId, String title);

    /**
     * Delete all lessons by course ID
     */
    @Modifying
    @Transactional
    int deleteByCourseId(Long courseId);

    /**
     * Find lessons by course ID
     */
    @Transactional(readOnly = true)
    List<Lesson> findByCourseId(Long courseId);

    /**
     * Count lessons in a course
     */
    @Transactional(readOnly = true)
    long countByCourseId(Long courseId);

    /**
     * Find next lesson by course and order index
     */
    @Transactional(readOnly = true)
    @Query("SELECT l FROM Lesson l WHERE l.course.id = :courseId AND l.orderIndex > :currentIndex ORDER BY l.orderIndex ASC")
    Optional<Lesson> findNextLesson(@Param("courseId") Long courseId, @Param("currentIndex") Integer currentIndex);

    /**
     * Find previous lesson by course and order index
     */
    @Transactional(readOnly = true)
    @Query("SELECT l FROM Lesson l WHERE l.course.id = :courseId AND l.orderIndex < :currentIndex ORDER BY l.orderIndex DESC")
    Optional<Lesson> findPreviousLesson(@Param("courseId") Long courseId, @Param("currentIndex") Integer currentIndex);
}
