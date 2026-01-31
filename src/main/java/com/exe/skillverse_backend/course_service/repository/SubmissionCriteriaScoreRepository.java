package com.exe.skillverse_backend.course_service.repository;

import com.exe.skillverse_backend.course_service.entity.SubmissionCriteriaScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubmissionCriteriaScoreRepository extends JpaRepository<SubmissionCriteriaScore, Long> {
    List<SubmissionCriteriaScore> findBySubmissionId(Long submissionId);
    void deleteBySubmissionId(Long submissionId);
}
