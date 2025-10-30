package com.exe.skillverse_backend.business_service.repository;

import com.exe.skillverse_backend.business_service.entity.JobPosting;
import com.exe.skillverse_backend.business_service.entity.enums.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface JobPostingRepository extends JpaRepository<JobPosting, Long> {

    /**
     * Find all jobs by recruiter ID ordered by created date descending
     */
    List<JobPosting> findByRecruiterProfileUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * Find all jobs by status ordered by created date descending
     */
    List<JobPosting> findByStatusOrderByCreatedAtDesc(JobStatus status);

    /**
     * Find job by ID and recruiter ID (for ownership validation)
     */
    Optional<JobPosting> findByIdAndRecruiterProfileUserId(Long id, Long userId);

    /**
     * Find jobs by status and deadline before given date (for auto-close scheduler)
     */
    List<JobPosting> findByStatusAndDeadlineBefore(JobStatus status, LocalDate date);
}
