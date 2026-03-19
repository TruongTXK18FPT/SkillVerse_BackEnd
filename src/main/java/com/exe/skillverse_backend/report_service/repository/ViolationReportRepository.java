package com.exe.skillverse_backend.report_service.repository;

import com.exe.skillverse_backend.report_service.entity.ViolationReport.ReportSeverity;
import com.exe.skillverse_backend.report_service.entity.ViolationReport.ReportStatus;
import com.exe.skillverse_backend.report_service.entity.ViolationReport.ReportType;
import com.exe.skillverse_backend.report_service.entity.ViolationReport;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for ViolationReport entity
 */
@Repository
public interface ViolationReportRepository extends JpaRepository<ViolationReport, Long> {

    /**
     * Find report by its unique code
     */
    Optional<ViolationReport> findByReportCode(String reportCode);

    /**
     * Check if report code already exists
     */
    boolean existsByReportCode(String reportCode);

    /**
     * Find all reports by reporter ID
     */
    List<ViolationReport> findByReporterIdOrderByCreatedAtDesc(Long reporterId);

    /**
     * Find all reports against a specific user
     */
    List<ViolationReport> findByReportedUserIdOrderByCreatedAtDesc(Long reportedUserId);

    /**
     * Find reports by status
     */
    Page<ViolationReport> findByStatus(ReportStatus status, Pageable pageable);

    /**
     * Find reports assigned to a specific admin
     */
    Page<ViolationReport> findByAssignedAdminId(Long adminId, Pageable pageable);

    /**
     * Count reports by status
     */
    long countByStatus(ReportStatus status);

    /**
     * Count reports by severity
     */
    long countBySeverity(ReportSeverity severity);

    /**
     * Count reports created after a specific date
     */
    long countByCreatedAtAfter(LocalDateTime date);

    /**
     * Find reports with filters
     */
    @Query("SELECT r FROM ViolationReport r WHERE "
            + "(:status IS NULL OR r.status = :status) AND "
            + "(:reportType IS NULL OR r.reportType = :reportType) AND "
            + "(:severity IS NULL OR r.severity = :severity) "
            + "ORDER BY r.createdAt DESC")
    Page<ViolationReport> findWithFilters(
            @Param("status") ReportStatus status,
            @Param("reportType") ReportType reportType,
            @Param("severity") ReportSeverity severity,
            Pageable pageable);

    /**
     * Count reports by type
     */
    @Query("SELECT r.reportType, COUNT(r) FROM ViolationReport r GROUP BY r.reportType")
    List<Object[]> countByReportType();

    /**
     * Count reports by severity
     */
    @Query("SELECT r.severity, COUNT(r) FROM ViolationReport r GROUP BY r.severity")
    List<Object[]> countBySeverityGrouped();

    /**
     * Find pending high severity reports
     */
    @Query("SELECT r FROM ViolationReport r WHERE r.status = 'PENDING' AND r.severity = 'HIGH' ORDER BY r.createdAt ASC")
    List<ViolationReport> findPendingHighSeverityReports();

    /**
     * Check if user has already reported another user for the same type (prevent spam reports)
     */
    @Query("SELECT COUNT(r) > 0 FROM ViolationReport r WHERE "
            + "r.reporter.id = :reporterId AND "
            + "r.reportedUser.id = :reportedUserId AND "
            + "r.reportType = :reportType AND "
            + "r.status IN ('PENDING', 'INVESTIGATING')")
    boolean existsPendingReport(
            @Param("reporterId") Long reporterId,
            @Param("reportedUserId") Long reportedUserId,
            @Param("reportType") ReportType reportType);
}
