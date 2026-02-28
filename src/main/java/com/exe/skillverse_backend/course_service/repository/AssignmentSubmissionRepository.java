package com.exe.skillverse_backend.course_service.repository;

import com.exe.skillverse_backend.course_service.entity.AssignmentSubmission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface AssignmentSubmissionRepository extends JpaRepository<AssignmentSubmission, Long> {

    /**
     * Find submissions by assignment ID with pagination
     */
    @Transactional(readOnly = true)
    @Query("SELECT asub FROM AssignmentSubmission asub WHERE asub.assignment.id = :assignmentId")
    Page<AssignmentSubmission> findByAssignmentId(@Param("assignmentId") Long assignmentId, Pageable pageable);

    /**
     * Find submission by assignment ID and user ID
     */
    @Transactional(readOnly = true)
    @Query("SELECT asub FROM AssignmentSubmission asub WHERE asub.assignment.id = :assignmentId AND asub.user.id = :userId")
    Optional<AssignmentSubmission> findByAssignmentIdAndUserId(@Param("assignmentId") Long assignmentId,
            @Param("userId") Long userId);

    /**
     * Calculate average score for an assignment
     */
    @Transactional(readOnly = true)
    @Query("SELECT AVG(asub.score) FROM AssignmentSubmission asub WHERE asub.assignment.id = :assignmentId AND asub.score IS NOT NULL")
    Optional<BigDecimal> averageScore(@Param("assignmentId") Long assignmentId);

    /**
     * Find submissions by user ID
     */
    @Transactional(readOnly = true)
    @Query("SELECT asub FROM AssignmentSubmission asub WHERE asub.user.id = :userId")
    Page<AssignmentSubmission> findByUserId(@Param("userId") Long userId, Pageable pageable);

    /**
     * Find ungraded submissions
     */
    @Transactional(readOnly = true)
    @Query("SELECT asub FROM AssignmentSubmission asub WHERE asub.score IS NULL")
    Page<AssignmentSubmission> findUngradedSubmissions(Pageable pageable);

    /**
     * Find graded submissions by assignment
     */
    @Transactional(readOnly = true)
    @Query("SELECT asub FROM AssignmentSubmission asub WHERE asub.assignment.id = :assignmentId AND asub.score IS NOT NULL")
    List<AssignmentSubmission> findGradedSubmissionsByAssignmentId(@Param("assignmentId") Long assignmentId);

    /**
     * Count submissions for assignment
     */
    @Transactional(readOnly = true)
    @Query("SELECT COUNT(asub) FROM AssignmentSubmission asub WHERE asub.assignment.id = :assignmentId")
    long countByAssignmentId(@Param("assignmentId") Long assignmentId);

    /**
     * Find highest score for assignment
     */
    @Transactional(readOnly = true)
    @Query("SELECT MAX(asub.score) FROM AssignmentSubmission asub WHERE asub.assignment.id = :assignmentId")
    Optional<BigDecimal> findHighestScoreByAssignmentId(@Param("assignmentId") Long assignmentId);

    /**
     * Find submissions by grader
     */
    @Transactional(readOnly = true)
    @Query("SELECT asub FROM AssignmentSubmission asub WHERE asub.gradedBy.id = :graderId")
    Page<AssignmentSubmission> findByGraderId(@Param("graderId") Long graderId, Pageable pageable);

    /**
     * Check if user has submitted assignment
     */
    @Transactional(readOnly = true)
    @Query("SELECT CASE WHEN COUNT(asub) > 0 THEN true ELSE false END " +
            "FROM AssignmentSubmission asub WHERE asub.assignment.id = :assignmentId AND asub.user.id = :userId")
    boolean hasUserSubmitted(@Param("assignmentId") Long assignmentId, @Param("userId") Long userId);

    @Transactional(readOnly = true)
    @Query("SELECT COUNT(asub) FROM AssignmentSubmission asub WHERE asub.user.id = :userId AND asub.score IS NOT NULL")
    long countCompletedProjectsByUserId(@Param("userId") Long userId);

    // ===== Version tracking queries (Coursera 2-version pattern) =====

    /**
     * Find all submissions by user for assignment, ordered by attemptNumber DESC (newest first)
     */
    @Transactional(readOnly = true)
    @Query("SELECT asub FROM AssignmentSubmission asub " +
            "WHERE asub.assignment.id = :assignmentId AND asub.user.id = :userId " +
            "ORDER BY asub.attemptNumber DESC")
    List<AssignmentSubmission> findByAssignmentIdAndUserIdOrderByAttemptNumberDesc(
            @Param("assignmentId") Long assignmentId, 
            @Param("userId") Long userId);

    /**
     * Find newest submission (isNewest=true) for a user on an assignment
     */
    @Transactional(readOnly = true)
    @Query("SELECT asub FROM AssignmentSubmission asub " +
            "WHERE asub.assignment.id = :assignmentId AND asub.user.id = :userId AND asub.isNewest = true")
    Optional<AssignmentSubmission> findNewestByAssignmentIdAndUserId(
            @Param("assignmentId") Long assignmentId, 
            @Param("userId") Long userId);

    /**
     * Find all newest submissions for an assignment (for mentor grading dashboard)
     */
    @Transactional(readOnly = true)
    @Query("SELECT asub FROM AssignmentSubmission asub " +
            "WHERE asub.assignment.id = :assignmentId AND asub.isNewest = true " +
            "ORDER BY asub.submittedAt DESC")
    List<AssignmentSubmission> findLatestSubmissionsByAssignmentId(@Param("assignmentId") Long assignmentId);

    /**
     * Find pending (ungraded) newest submissions for an assignment
     */
    @Transactional(readOnly = true)
    @Query("SELECT asub FROM AssignmentSubmission asub " +
            "WHERE asub.assignment.id = :assignmentId AND asub.isNewest = true AND asub.score IS NULL " +
            "ORDER BY asub.submittedAt ASC")
    List<AssignmentSubmission> findPendingSubmissionsByAssignmentId(@Param("assignmentId") Long assignmentId);

    /**
     * Count pending submissions for an assignment (for badge display)
     */
    @Transactional(readOnly = true)
    @Query("SELECT COUNT(asub) FROM AssignmentSubmission asub " +
            "WHERE asub.assignment.id = :assignmentId AND asub.isNewest = true AND asub.score IS NULL")
    long countPendingByAssignmentId(@Param("assignmentId") Long assignmentId);

    /**
     * Find both newest and previous submissions for a user (for version comparison)
     */
    @Transactional(readOnly = true)
    @Query("SELECT asub FROM AssignmentSubmission asub " +
            "WHERE asub.assignment.id = :assignmentId AND asub.user.id = :userId " +
            "AND (asub.isNewest = true OR asub.isPrevious = true) " +
            "ORDER BY asub.attemptNumber DESC")
    List<AssignmentSubmission> findVersionsForComparison(
            @Param("assignmentId") Long assignmentId, 
            @Param("userId") Long userId);
    /**
     * Find all pending (ungraded newest) submissions across all assignments
     * owned by a specific mentor/author. Single-query replacement for N+1 pattern.
     */
    @Transactional(readOnly = true)
    @Query("SELECT asub FROM AssignmentSubmission asub " +
            "JOIN asub.assignment a " +
            "JOIN a.module m " +
            "JOIN m.course c " +
            "WHERE c.author.id = :authorId " +
            "AND asub.isNewest = true AND asub.score IS NULL " +
            "ORDER BY asub.submittedAt ASC")
    List<AssignmentSubmission> findAllPendingByAuthorId(@Param("authorId") Long authorId);
}
