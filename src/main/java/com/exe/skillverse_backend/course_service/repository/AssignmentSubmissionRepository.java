package com.exe.skillverse_backend.course_service.repository;

import com.exe.skillverse_backend.course_service.entity.AssignmentSubmission;
import java.math.BigDecimal;
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
public interface AssignmentSubmissionRepository extends JpaRepository<AssignmentSubmission, Long> {

    /**
     * Eagerly fetch assignment to avoid LazyInitializationException in async contexts.
     */
    @Transactional(readOnly = true)
    @Query("SELECT s FROM AssignmentSubmission s JOIN FETCH s.assignment WHERE s.id = :id")
    Optional<AssignmentSubmission> findByIdWithAssignment(@Param("id") Long id);

    /**
     * Eagerly fetch full chain (assignment → module → course → author) to avoid N+1
     * queries in async contexts. Use for operations that access mentor/course metadata.
     */
    @Transactional(readOnly = true)
    @Query("SELECT s FROM AssignmentSubmission s " +
           "JOIN FETCH s.assignment a " +
           "JOIN FETCH a.module m " +
           "JOIN FETCH m.course c " +
           "JOIN FETCH c.author " +
           "WHERE s.id = :id")
    Optional<AssignmentSubmission> findByIdWithFullChain(@Param("id") Long id);

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
     * Find all newest submissions for an assignment (for mentor grading dashboard).
     * Supports pagination via Pageable so all records are NOT loaded at once.
     */
    @Transactional(readOnly = true)
    @Query("SELECT asub FROM AssignmentSubmission asub " +
            "WHERE asub.assignment.id = :assignmentId AND asub.isNewest = true " +
            "ORDER BY asub.submittedAt DESC")
    Page<AssignmentSubmission> findLatestSubmissionsByAssignmentId(
            @Param("assignmentId") Long assignmentId,
            Pageable pageable);

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

    /**
     * Find all newest submissions across all assignments owned by a specific mentor/author.
     */
    @Transactional(readOnly = true)
    @Query("SELECT asub FROM AssignmentSubmission asub " +
            "JOIN asub.assignment a " +
            "JOIN a.module m " +
            "JOIN m.course c " +
            "WHERE c.author.id = :authorId " +
            "AND asub.isNewest = true " +
            "ORDER BY asub.submittedAt DESC")
    List<AssignmentSubmission> findAllLatestByAuthorId(@Param("authorId") Long authorId);

    @Transactional(readOnly = true)
    @Query("SELECT asub FROM AssignmentSubmission asub " +
            "JOIN asub.assignment a " +
            "JOIN a.module m " +
            "JOIN m.course c " +
            "LEFT JOIN asub.user u " +
            "WHERE c.author.id = :authorId " +
            "AND asub.isNewest = true " +
            "AND (" +
            "  :filter = 'ALL' " +
            "  OR (:filter = 'PENDING' AND asub.score IS NULL) " +
            "  OR (:filter = 'GRADED' AND asub.score IS NOT NULL) " +
            "  OR (:filter = 'LATE' AND a.dueAt IS NOT NULL AND asub.submittedAt > a.dueAt)" +
            ") " +
            "AND (" +
            "  :search IS NULL OR :search = '' " +
            "  OR LOWER(CONCAT(CONCAT(COALESCE(u.firstName, ''), ' '), COALESCE(u.lastName, ''))) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "  OR LOWER(COALESCE(u.email, '')) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "  OR LOWER(COALESCE(c.title, '')) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "  OR LOWER(COALESCE(m.title, '')) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "  OR LOWER(COALESCE(a.title, '')) LIKE LOWER(CONCAT('%', :search, '%'))" +
            ") " +
            "ORDER BY CASE WHEN asub.score IS NULL THEN 0 ELSE 1 END, asub.submittedAt DESC")
    Page<AssignmentSubmission> findMentorLatestByAuthorIdWithFilters(
            @Param("authorId") Long authorId,
            @Param("filter") String filter,
            @Param("search") String search,
            Pageable pageable
    );

    @Transactional(readOnly = true)
    @Query("SELECT COUNT(asub) FROM AssignmentSubmission asub " +
            "JOIN asub.assignment a " +
            "JOIN a.module m " +
            "JOIN m.course c " +
            "WHERE c.author.id = :authorId " +
            "AND asub.isNewest = true")
    long countAllLatestByAuthorId(@Param("authorId") Long authorId);

    @Transactional(readOnly = true)
    @Query("SELECT COUNT(asub) FROM AssignmentSubmission asub " +
            "JOIN asub.assignment a " +
            "JOIN a.module m " +
            "JOIN m.course c " +
            "WHERE c.author.id = :authorId " +
            "AND asub.isNewest = true " +
            "AND asub.score IS NULL")
    long countPendingLatestByAuthorId(@Param("authorId") Long authorId);

    @Transactional(readOnly = true)
    @Query("SELECT COUNT(asub) FROM AssignmentSubmission asub " +
            "JOIN asub.assignment a " +
            "JOIN a.module m " +
            "JOIN m.course c " +
            "WHERE c.author.id = :authorId " +
            "AND asub.isNewest = true " +
            "AND asub.score IS NOT NULL")
    long countGradedLatestByAuthorId(@Param("authorId") Long authorId);

    @Transactional(readOnly = true)
    @Query("SELECT COUNT(asub) FROM AssignmentSubmission asub " +
            "JOIN asub.assignment a " +
            "JOIN a.module m " +
            "JOIN m.course c " +
            "WHERE c.author.id = :authorId " +
            "AND asub.isNewest = true " +
            "AND a.dueAt IS NOT NULL " +
            "AND asub.submittedAt > a.dueAt")
    long countLateLatestByAuthorId(@Param("authorId") Long authorId);

    @Transactional(readOnly = true)
    @Query("SELECT DISTINCT asub.assignment.id FROM AssignmentSubmission asub " +
            "WHERE asub.user.id = :userId " +
            "AND asub.assignment.module.course.id = :courseId " +
            "AND asub.isNewest = true " +
            "AND asub.isPassed = true")
    List<Long> findPassedAssignmentIdsByCourseAndUser(@Param("courseId") Long courseId,
                                                      @Param("userId") Long userId);

    @Transactional(readOnly = true)
    @Query("SELECT asub FROM AssignmentSubmission asub " +
            "JOIN FETCH asub.assignment a " +
            "WHERE asub.user.id = :userId " +
            "AND a.module.course.id = :courseId " +
            "AND asub.isNewest = true " +
            "AND asub.isPassed = true")
    List<AssignmentSubmission> findLatestPassedNewestByCourseAndUser(@Param("courseId") Long courseId,
                                                                      @Param("userId") Long userId);

    @Transactional(readOnly = true)
    @Query("SELECT DISTINCT asub.assignment.id FROM AssignmentSubmission asub " +
            "WHERE asub.user.id = :userId " +
            "AND asub.assignment.module.course.id = :courseId " +
            "AND asub.isNewest = true " +
            "AND asub.isPassed = true " +
            "AND (asub.assignment.isRequired = true OR asub.assignment.isRequired IS NULL)")
    List<Long> findPassedRequiredAssignmentIdsByCourseAndUser(@Param("courseId") Long courseId,
                                                              @Param("userId") Long userId);

    @Transactional(readOnly = true)
    @Query("SELECT CASE WHEN COUNT(asub) > 0 THEN true ELSE false END " +
            "FROM AssignmentSubmission asub " +
            "WHERE asub.assignment.module.course.id = :courseId " +
            "AND asub.user.id = :userId " +
            "AND asub.isNewest = true " +
            "AND asub.score IS NULL")
    boolean existsNewestPendingGradeByCourseAndUser(@Param("courseId") Long courseId,
                                                     @Param("userId") Long userId);

    // ===== AI Grading queries =====

    @Transactional(readOnly = true)
    @Query("SELECT COUNT(asub) FROM AssignmentSubmission asub WHERE asub.assignment.id = :assignmentId AND asub.score IS NOT NULL")
    long countByAssignmentIdAndScoreIsNotNull(@Param("assignmentId") Long assignmentId);

    @Transactional(readOnly = true)
    @Query("SELECT COUNT(asub) FROM AssignmentSubmission asub WHERE asub.assignment.id = :assignmentId AND asub.isAiGraded = true AND asub.mentorConfirmed IS NULL")
    long countByAssignmentIdAndIsAiGradedTrueAndMentorConfirmedNull(@Param("assignmentId") Long assignmentId);

    @Transactional(readOnly = true)
    @Query("SELECT COUNT(asub) FROM AssignmentSubmission asub WHERE asub.assignment.id = :assignmentId AND asub.score IS NULL")
    long countByAssignmentIdAndScoreIsNull(@Param("assignmentId") Long assignmentId);

    /**
     * Bulk clear isPrevious flag for older submissions when resubmitting.
     * Eliminates N queries when user resubmits assignment.
     */
    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("UPDATE AssignmentSubmission asub SET asub.isPrevious = false " +
           "WHERE asub.assignment.id = :assignmentId AND asub.user.id = :userId")
    int clearPreviousFlagForUser(@Param("assignmentId") Long assignmentId, @Param("userId") Long userId);

    // ===== AI Grading stats queries =====

    @Transactional(readOnly = true)
    long countByIsAiGradedTrue();

    @Transactional(readOnly = true)
    long countByIsAiGradedTrueAndMentorConfirmedTrue();

    @Transactional(readOnly = true)
    long countByDisputeFlagTrue();

    @org.springframework.data.jpa.repository.Query("SELECT COALESCE(SUM(asub.aiGradeAttemptCount), 0) FROM AssignmentSubmission asub WHERE asub.isAiGraded = true")
    long computedSumAiGradeAttemptCount();
}
