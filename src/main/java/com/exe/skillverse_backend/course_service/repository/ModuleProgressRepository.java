package com.exe.skillverse_backend.course_service.repository;

import com.exe.skillverse_backend.course_service.entity.ModuleProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

@Repository
public interface ModuleProgressRepository extends JpaRepository<ModuleProgress, Serializable> {

    /**
     * Find module progress by user ID and course ID (through module relationship)
     */
    @Transactional(readOnly = true)
    @Query("SELECT mp FROM ModuleProgress mp WHERE mp.user.id = :userId AND mp.module.course.id = :courseId")
    List<ModuleProgress> findByUserIdAndCourseId(@Param("userId") Long userId, @Param("courseId") Long courseId);

    /**
     * Find module progress by user ID and module ID
     */
    @Transactional(readOnly = true)
    @Query("SELECT mp FROM ModuleProgress mp WHERE mp.user.id = :userId AND mp.module.id = :moduleId")
    Optional<ModuleProgress> findByUserIdAndModuleId(@Param("userId") Long userId, @Param("moduleId") Long moduleId);

    /**
     * Calculate average progress percentage for user in a course
     */
    @Transactional(readOnly = true)
    @Query("SELECT AVG(CASE WHEN mp.status = 'COMPLETED' THEN 100.0 ELSE 50.0 END) " +
           "FROM ModuleProgress mp WHERE mp.user.id = :userId AND mp.module.course.id = :courseId")
    Optional<Double> avgProgress(@Param("userId") Long userId, @Param("courseId") Long courseId);

    /**
     * Find progress by module ID
     */
    @Transactional(readOnly = true)
    @Query("SELECT mp FROM ModuleProgress mp WHERE mp.module.id = :moduleId")
    List<ModuleProgress> findByModuleId(@Param("moduleId") Long moduleId);

    /**
     * Find progress by user ID
     */
    @Transactional(readOnly = true)
    @Query("SELECT mp FROM ModuleProgress mp WHERE mp.user.id = :userId")
    List<ModuleProgress> findByUserId(@Param("userId") Long userId);

    /**
     * Check if user has completed a module
     */
    @Transactional(readOnly = true)
    @Query("SELECT CASE WHEN COUNT(mp) > 0 THEN true ELSE false END " +
           "FROM ModuleProgress mp WHERE mp.user.id = :userId AND mp.module.id = :moduleId AND mp.status = 'COMPLETED'")
    boolean isModuleCompletedByUser(@Param("userId") Long userId, @Param("moduleId") Long moduleId);

    /**
     * Count completed modules by user in a course
     */
    @Transactional(readOnly = true)
    @Query("SELECT COUNT(mp) FROM ModuleProgress mp " +
           "WHERE mp.user.id = :userId AND mp.module.course.id = :courseId AND mp.status = 'COMPLETED'")
    long countCompletedModulesByUserInCourse(@Param("userId") Long userId, @Param("courseId") Long courseId);
}
