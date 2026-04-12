package com.exe.skillverse_backend.assignment_ai_service.repository;

import com.exe.skillverse_backend.course_service.entity.AssignmentSubmission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface AssignmentSubmissionAiRepository extends JpaRepository<AssignmentSubmission, Long> {

    long countByIsAiGradedTrue();

    long countByIsAiGradedTrueAndMentorConfirmedTrue();

    long countByIsAiGradedTrueAndMentorConfirmedNull();

    long countByIsAiGradedTrueAndDisputeFlagTrue();

    long countByIsAiGradedTrueAndAiConfidenceLessThan(Double threshold);

    @Query("SELECT COALESCE(SUM(s.aiGradeAttemptCount), 0) FROM AssignmentSubmission s WHERE s.isAiGraded = true")
    long sumAiGradeAttemptCount();

    @Query("SELECT COALESCE(AVG(ABS(s.aiScore - s.score)), 0) FROM AssignmentSubmission s " +
           "WHERE s.isAiGraded = true AND s.aiScore IS NOT NULL AND s.score IS NOT NULL")
    Double findAverageScoreDelta();

    @Query("SELECT COUNT(s) FROM AssignmentSubmission s " +
           "WHERE s.isAiGraded = true AND s.aiScore IS NOT NULL AND s.score IS NOT NULL")
    long countComparedSubmissions();

    @Query("SELECT COUNT(s) FROM AssignmentSubmission s WHERE s.assignment.id = :assignmentId AND s.score IS NOT NULL")
    long countByAssignmentIdAndScoreIsNotNull(Long assignmentId);

    @Query("SELECT COUNT(s) FROM AssignmentSubmission s WHERE s.assignment.id = :assignmentId AND s.isAiGraded = true")
    long countByAssignmentIdAndIsAiGradedTrue(Long assignmentId);

    @Query("SELECT COUNT(s) FROM AssignmentSubmission s WHERE s.assignment.id = :assignmentId AND s.isAiGraded = true AND s.mentorConfirmed IS NULL")
    long countByAssignmentIdAndIsAiGradedTrueAndMentorConfirmedNull(Long assignmentId);

    @Query("SELECT COUNT(s) FROM AssignmentSubmission s WHERE s.assignment.id = :assignmentId AND s.isAiGraded = true AND s.disputeFlag = true")
    long countByAssignmentIdAndIsAiGradedTrueAndDisputeFlagTrue(Long assignmentId);

    @Query("SELECT COUNT(s) FROM AssignmentSubmission s WHERE s.assignment.id = :assignmentId AND s.isAiGraded = true AND s.aiConfidence < :threshold")
    long countByAssignmentIdAndIsAiGradedTrueAndAiConfidenceLessThan(Long assignmentId, Double threshold);

    @Query("SELECT COALESCE(AVG(ABS(s.aiScore - s.score)), 0) FROM AssignmentSubmission s " +
           "WHERE s.assignment.id = :assignmentId AND s.isAiGraded = true AND s.aiScore IS NOT NULL AND s.score IS NOT NULL")
    Double findAverageScoreDeltaByAssignmentId(Long assignmentId);

    @Query("SELECT COUNT(s) FROM AssignmentSubmission s WHERE s.assignment.id = :assignmentId AND s.score IS NULL")
    long countByAssignmentIdAndScoreIsNull(Long assignmentId);

    @Query("SELECT s FROM AssignmentSubmission s WHERE s.isAiGraded = true " +
           "AND ((:status = 'all') OR " +
           "  (:status = 'confirmed' AND s.mentorConfirmed = true) OR " +
           "  (:status = 'pending' AND s.mentorConfirmed IS NULL) OR " +
           "  (:status = 'disputed' AND s.disputeFlag = true) OR " +
           "  (:status = 'exceptions' AND (s.disputeFlag = true OR s.mentorConfirmed IS NULL OR s.aiConfidence < 0.6 OR (s.aiScore IS NOT NULL AND s.score IS NOT NULL AND ABS(s.aiScore - s.score) >= 2)))) " +
           "AND (:exceptionOnly = false OR (s.disputeFlag = true OR s.mentorConfirmed IS NULL OR s.aiConfidence < 0.6 OR (s.aiScore IS NOT NULL AND s.score IS NOT NULL AND ABS(s.aiScore - s.score) >= 2))) " +
           "AND ((:search IS NULL) OR (:search = '') OR " +
           "  (LOWER(CONCAT(COALESCE(s.user.firstName, ''), ' ', COALESCE(s.user.lastName, ''))) LIKE LOWER(CONCAT('%', :search, '%'))) OR " +
           "  (LOWER(CONCAT(COALESCE(s.gradedBy.firstName, ''), ' ', COALESCE(s.gradedBy.lastName, ''))) LIKE LOWER(CONCAT('%', :search, '%'))) OR " +
           "  (LOWER(s.assignment.title) LIKE LOWER(CONCAT('%', :search, '%'))) OR " +
           "  (LOWER(s.assignment.module.title) LIKE LOWER(CONCAT('%', :search, '%'))) OR " +
           "  (LOWER(s.assignment.module.course.title) LIKE LOWER(CONCAT('%', :search, '%'))))")
    Page<AssignmentSubmission> findAiGradedSubmissions(String status, String search, boolean exceptionOnly, Pageable pageable);
}
