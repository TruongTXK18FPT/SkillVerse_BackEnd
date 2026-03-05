package com.exe.skillverse_backend.business_service.repository;

import com.exe.skillverse_backend.business_service.entity.JobStatusAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface JobStatusAuditLogRepository extends JpaRepository<JobStatusAuditLog, Long> {

    // Find by job
    List<JobStatusAuditLog> findByJobIdOrderByCreatedAtDesc(Long jobId);

    // Find by short term job
    List<JobStatusAuditLog> findByShortTermJobIdOrderByCreatedAtDesc(Long shortTermJobId);

    // Find by application
    List<JobStatusAuditLog> findByApplicationIdOrderByCreatedAtDesc(Long applicationId);

    // Find by user who made the change
    List<JobStatusAuditLog> findByChangedByIdOrderByCreatedAtDesc(Long userId);

    Page<JobStatusAuditLog> findByChangedById(Long userId, Pageable pageable);

    // Find by date range
    List<JobStatusAuditLog> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate);

    // Find by status
    List<JobStatusAuditLog> findByNewStatusOrderByCreatedAtDesc(String newStatus);
}
