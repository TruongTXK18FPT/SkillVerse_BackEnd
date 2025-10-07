package com.exe.skillverse_backend.course_service.repository;

import com.exe.skillverse_backend.course_service.entity.Assignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

    /**
     * Find assignment by ID and module ID
     */
    @Transactional(readOnly = true)
    @Query("SELECT a FROM Assignment a WHERE a.id = :id AND a.module.id = :moduleId")
    Optional<Assignment> findByIdAndModuleId(@Param("id") Long id, @Param("moduleId") Long moduleId);

    /**
     * Find assignments by module ID ordered by due date ascending
     */
    @Transactional(readOnly = true)
    @Query("SELECT a FROM Assignment a WHERE a.module.id = :moduleId ORDER BY a.dueAt ASC")
    List<Assignment> findByModuleIdOrderByDueAtAsc(@Param("moduleId") Long moduleId);

    /**
     * Find assignments by module ID
     */
    @Transactional(readOnly = true)
    @Query("SELECT a FROM Assignment a WHERE a.module.id = :moduleId")
    List<Assignment> findByModuleId(@Param("moduleId") Long moduleId);

    /**
     * Find assignments by course ID
     */
    @Transactional(readOnly = true)
    @Query("SELECT a FROM Assignment a WHERE a.module.course.id = :courseId")
    List<Assignment> findByCourseId(@Param("courseId") Long courseId);

    /**
     * Find overdue assignments
     */
    @Transactional(readOnly = true)
    @Query("SELECT a FROM Assignment a WHERE a.dueAt < :now")
    List<Assignment> findOverdueAssignments(@Param("now") java.time.Instant now);

    /**
     * Find upcoming assignments (due within specified days)
     */
    @Transactional(readOnly = true)
    @Query("SELECT a FROM Assignment a WHERE a.dueAt BETWEEN :now AND :deadline")
    List<Assignment> findUpcomingAssignments(@Param("now") java.time.Instant now, @Param("deadline") java.time.Instant deadline);

    /**
     * Count assignments in a course
     */
    @Transactional(readOnly = true)
    @Query("SELECT COUNT(a) FROM Assignment a WHERE a.module.course.id = :courseId")
    long countByCourseId(@Param("courseId") Long courseId);

    /**
     * Check if assignment exists for module
     */
    @Transactional(readOnly = true)
    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM Assignment a WHERE a.module.id = :moduleId")
    boolean existsByModuleId(@Param("moduleId") Long moduleId);
}
