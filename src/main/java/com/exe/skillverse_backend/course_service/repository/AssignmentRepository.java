package com.exe.skillverse_backend.course_service.repository;

import com.exe.skillverse_backend.course_service.entity.Assignment;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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
     * Find assignments by module ID ordered by orderIndex ascending.
     * Fallback by id to keep deterministic ordering when orderIndex collides.
     */
    @Transactional(readOnly = true)
    @Query("SELECT a FROM Assignment a WHERE a.module.id = :moduleId ORDER BY a.orderIndex ASC, a.id ASC")
    List<Assignment> findByModuleIdOrderByOrderIndexAsc(@Param("moduleId") Long moduleId);

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
    List<Assignment> findOverdueAssignments(@Param("now") Instant now);

    /**
     * Find upcoming assignments (due within specified days)
     */
    @Transactional(readOnly = true)
    @Query("SELECT a FROM Assignment a WHERE a.dueAt BETWEEN :now AND :deadline")
    List<Assignment> findUpcomingAssignments(@Param("now") Instant now, @Param("deadline") Instant deadline);

    /**
     * Count assignments in a course
     */
    @Transactional(readOnly = true)
    @Query("SELECT COUNT(a) FROM Assignment a WHERE a.module.course.id = :courseId")
    long countByCourseId(@Param("courseId") Long courseId);

    /**
     * Count required assignments in a course.
     */
    @Transactional(readOnly = true)
    @Query("SELECT COUNT(a) FROM Assignment a WHERE a.module.course.id = :courseId AND (a.isRequired = true OR a.isRequired IS NULL)")
    long countRequiredByCourseId(@Param("courseId") Long courseId);

    /**
     * Check if assignment exists for module
     */
    @Transactional(readOnly = true)
    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM Assignment a WHERE a.module.id = :moduleId")
    boolean existsByModuleId(@Param("moduleId") Long moduleId);

    @Transactional(readOnly = true)
    long countByAiGradingEnabledTrue();

    @Transactional(readOnly = true)
    long countByAiGradingEnabledTrueAndTrustAiEnabledTrue();

    @Transactional(readOnly = true)
    @Query("SELECT COUNT(a) FROM Assignment a WHERE a.aiGradingEnabled = true AND a.aiGradingPrompt IS NOT NULL AND TRIM(a.aiGradingPrompt) <> ''")
    long countCustomPromptEnabledAssignments();

    @Transactional(readOnly = true)
    long countByAiGradingEnabledTrueAndGradingStyle(String gradingStyle);

    @Transactional(readOnly = true)
    @Query("""
        SELECT a FROM Assignment a
        WHERE a.aiGradingEnabled = true
          AND ((:search IS NULL) OR (:search = '') OR
            LOWER(a.title) LIKE LOWER(CONCAT('%', :search, '%')) OR
            LOWER(a.module.title) LIKE LOWER(CONCAT('%', :search, '%')) OR
            LOWER(a.module.course.title) LIKE LOWER(CONCAT('%', :search, '%')) OR
            LOWER(CONCAT(COALESCE(a.module.course.author.firstName, ''), ' ', COALESCE(a.module.course.author.lastName, '')))
              LIKE LOWER(CONCAT('%', :search, '%')))
          AND (:trustAiEnabled IS NULL OR a.trustAiEnabled = :trustAiEnabled)
          AND (:hasCustomPrompt IS NULL OR
            (:hasCustomPrompt = true AND a.aiGradingPrompt IS NOT NULL AND TRIM(a.aiGradingPrompt) <> '') OR
            (:hasCustomPrompt = false AND (a.aiGradingPrompt IS NULL OR TRIM(a.aiGradingPrompt) = '')))
          AND (:gradingStyle IS NULL OR a.gradingStyle = :gradingStyle)
        """)
    Page<Assignment> findAiGradingAssignments(
            @Param("search") String search,
            @Param("trustAiEnabled") Boolean trustAiEnabled,
            @Param("hasCustomPrompt") Boolean hasCustomPrompt,
            @Param("gradingStyle") String gradingStyle,
            Pageable pageable);
}
