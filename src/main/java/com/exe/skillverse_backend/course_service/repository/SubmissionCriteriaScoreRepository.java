package com.exe.skillverse_backend.course_service.repository;

import com.exe.skillverse_backend.course_service.entity.SubmissionCriteriaScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubmissionCriteriaScoreRepository extends JpaRepository<SubmissionCriteriaScore, Long> {
    List<SubmissionCriteriaScore> findBySubmissionId(Long submissionId);
    void deleteBySubmissionId(Long submissionId);
    void deleteByCriteriaId(Long criteriaId);

    /**
     * Bulk-delete all criteria scores for every submission belonging to an assignment.
     * Used before cascading assignment deletion to prevent FK violations.
     */
    @Modifying
    @Query("DELETE FROM SubmissionCriteriaScore scs WHERE scs.submission.assignment.id = :assignmentId")
    void deleteByAssignmentId(@Param("assignmentId") Long assignmentId);
}
