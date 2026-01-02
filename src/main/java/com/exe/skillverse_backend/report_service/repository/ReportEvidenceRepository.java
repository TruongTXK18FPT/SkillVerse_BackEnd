package com.exe.skillverse_backend.report_service.repository;

import com.exe.skillverse_backend.report_service.entity.ReportEvidence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository interface for ReportEvidence entity
 */
@Repository
public interface ReportEvidenceRepository extends JpaRepository<ReportEvidence, Long> {

    /**
     * Find all evidences for a specific violation report
     */
    List<ReportEvidence> findByViolationReportId(Long violationReportId);

    /**
     * Delete all evidences for a specific violation report
     */
    void deleteByViolationReportId(Long violationReportId);

    /**
     * Count evidences for a specific report
     */
    long countByViolationReportId(Long violationReportId);
}
