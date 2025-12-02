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
}
