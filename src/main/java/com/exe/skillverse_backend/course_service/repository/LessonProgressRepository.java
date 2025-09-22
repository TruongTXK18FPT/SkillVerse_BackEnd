package com.exe.skillverse_backend.course_service.repository;

import com.exe.skillverse_backend.course_service.entity.LessonProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

@Repository
public interface LessonProgressRepository extends JpaRepository<LessonProgress, Serializable> {

    /**
     * Find lesson progress by user ID and course ID (through lesson relationship)
     */
    @Transactional(readOnly = true)
    @Query("SELECT lp FROM LessonProgress lp WHERE lp.user.id = :userId AND lp.lesson.course.id = :courseId")
    List<LessonProgress> findByUserIdAndCourseId(@Param("userId") Long userId, @Param("courseId") Long courseId);

    /**
     * Find lesson progress by user ID and lesson ID
     */
    @Transactional(readOnly = true)
    @Query("SELECT lp FROM LessonProgress lp WHERE lp.user.id = :userId AND lp.lesson.id = :lessonId")
    Optional<LessonProgress> findByUserIdAndLessonId(@Param("userId") Long userId, @Param("lessonId") Long lessonId);

    /**
     * Calculate average progress percentage for user in a course
     */
    @Transactional(readOnly = true)
    @Query("SELECT AVG(CASE WHEN lp.status = 'COMPLETED' THEN 100.0 ELSE 50.0 END) " +
           "FROM LessonProgress lp WHERE lp.user.id = :userId AND lp.lesson.course.id = :courseId")
    Optional<Double> avgProgress(@Param("userId") Long userId, @Param("courseId") Long courseId);

    /**
     * Find progress by lesson ID
     */
    @Transactional(readOnly = true)
    @Query("SELECT lp FROM LessonProgress lp WHERE lp.lesson.id = :lessonId")
    List<LessonProgress> findByLessonId(@Param("lessonId") Long lessonId);

    /**
     * Find progress by user ID
     */
    @Transactional(readOnly = true)
    @Query("SELECT lp FROM LessonProgress lp WHERE lp.user.id = :userId")
    List<LessonProgress> findByUserId(@Param("userId") Long userId);

    /**
     * Check if user has completed a lesson
     */
    @Transactional(readOnly = true)
    @Query("SELECT CASE WHEN COUNT(lp) > 0 THEN true ELSE false END " +
           "FROM LessonProgress lp WHERE lp.user.id = :userId AND lp.lesson.id = :lessonId AND lp.status = 'COMPLETED'")
    boolean isLessonCompletedByUser(@Param("userId") Long userId, @Param("lessonId") Long lessonId);

    /**
     * Count completed lessons by user in a course
     */
    @Transactional(readOnly = true)
    @Query("SELECT COUNT(lp) FROM LessonProgress lp " +
           "WHERE lp.user.id = :userId AND lp.lesson.course.id = :courseId AND lp.status = 'COMPLETED'")
    long countCompletedLessonsByUserInCourse(@Param("userId") Long userId, @Param("courseId") Long courseId);
}
