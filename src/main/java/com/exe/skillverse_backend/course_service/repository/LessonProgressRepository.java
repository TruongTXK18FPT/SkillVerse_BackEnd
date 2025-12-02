package com.exe.skillverse_backend.course_service.repository;

import com.exe.skillverse_backend.course_service.entity.LessonProgress;
import com.exe.skillverse_backend.course_service.entity.LessonProgressId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LessonProgressRepository extends JpaRepository<LessonProgress, LessonProgressId> {

    @Query("SELECT COUNT(lp) > 0 FROM LessonProgress lp WHERE lp.user.id = :userId AND lp.lesson.id = :lessonId")
    boolean existsByUserAndLesson(@Param("userId") Long userId, @Param("lessonId") Long lessonId);

    @Query("SELECT COUNT(lp) FROM LessonProgress lp WHERE lp.user.id = :userId AND lp.lesson.module.id = :moduleId AND lp.completed = true")
    long countCompletedInModule(@Param("userId") Long userId, @Param("moduleId") Long moduleId);

    @Query("SELECT DISTINCT CAST(lp.completedAt AS LocalDate) FROM LessonProgress lp WHERE lp.user.id = :userId AND lp.completed = true ORDER BY CAST(lp.completedAt AS LocalDate) DESC")
    List<java.time.LocalDate> findDistinctCompletionDatesByUserId(@Param("userId") Long userId);

    @Query("SELECT DISTINCT CAST(lp.completedAt AS LocalDate) FROM LessonProgress lp WHERE lp.user.id = :userId AND lp.completed = true AND lp.completedAt >= :startOfWeek")
    List<java.time.LocalDate> findCompletionDatesSince(@Param("userId") Long userId,
            @Param("startOfWeek") java.time.Instant startOfWeek);
}
