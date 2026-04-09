package com.exe.skillverse_backend.report_service.service.impl;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.notification_service.entity.NotificationType;
import com.exe.skillverse_backend.notification_service.service.NotificationService;
import com.exe.skillverse_backend.report_service.dto.request.CreateViolationReportRequest;
import com.exe.skillverse_backend.report_service.dto.request.UpdateViolationReportRequest;
import com.exe.skillverse_backend.report_service.dto.response.ViolationReportResponse;
import com.exe.skillverse_backend.report_service.dto.response.ViolationReportStatsResponse;
import com.exe.skillverse_backend.report_service.entity.ReportEvidence.EvidenceType;
import com.exe.skillverse_backend.report_service.entity.ReportEvidence;
import com.exe.skillverse_backend.report_service.entity.ViolationReport.ReportSeverity;
import com.exe.skillverse_backend.report_service.entity.ViolationReport.ReportStatus;
import com.exe.skillverse_backend.report_service.entity.ViolationReport.ReportType;
import com.exe.skillverse_backend.report_service.entity.ViolationReport.ResolutionAction;
import com.exe.skillverse_backend.report_service.entity.ViolationReport;
import com.exe.skillverse_backend.report_service.repository.ReportEvidenceRepository;
import com.exe.skillverse_backend.report_service.repository.ViolationReportRepository;
import com.exe.skillverse_backend.report_service.service.ViolationReportService;
import com.exe.skillverse_backend.shared.exception.BadRequestException;
import com.exe.skillverse_backend.shared.exception.ForbiddenException;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import com.exe.skillverse_backend.shared.service.EmailService;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of ViolationReportService
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ViolationReportServiceImpl implements ViolationReportService {

    private final ViolationReportRepository reportRepository;
    private final ReportEvidenceRepository evidenceRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final NotificationService notificationService;

    // ==================== User Operations ====================

    @Override
    @Transactional
    public ViolationReportResponse createReport(Long reporterId, CreateViolationReportRequest request) {
        log.info("Creating violation report by user: {} against user: {} / email: {}", 
                reporterId, request.getReportedUserId(), request.getReportedUserEmail());

        // Validate reporter exists
        User reporter = userRepository.findById(reporterId)
                .orElseThrow(() -> new NotFoundException("Reporter user not found with id: " + reporterId));

        // Validate reported user exists - support both ID, Email, or neither (anonymous report)
        User reportedUser = null;
        if (request.getReportedUserId() != null && request.getReportedUserId() > 0) {
            reportedUser = userRepository.findById(request.getReportedUserId())
                    .orElseThrow(() -> new NotFoundException("Reported user not found with id: " + request.getReportedUserId()));
        } else if (request.getReportedUserEmail() != null && !request.getReportedUserEmail().isBlank()) {
            reportedUser = userRepository.findByEmail(request.getReportedUserEmail())
                    .orElseThrow(() -> new NotFoundException("Không tìm thấy người dùng với email: " + request.getReportedUserEmail()));
        }
        // If neither is provided, reportedUser remains null (anonymous report) - allowed

        // Prevent self-reporting
        if (reportedUser != null && reporterId.equals(reportedUser.getId())) {
            throw new BadRequestException("You cannot report yourself");
        }

        // Parse report type
        ReportType reportType;
        try {
            reportType = ReportType.valueOf(request.getReportType().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid report type: " + request.getReportType());
        }

        // Check for duplicate pending reports (only if reportedUser is known)
        if (reportedUser != null && reportRepository.existsPendingReport(reporterId, reportedUser.getId(), reportType)) {
            throw new BadRequestException(
                    "You already have a pending report of this type against this user. Please wait for it to be processed.");
        }

        // Parse severity
        ReportSeverity severity = ReportSeverity.MEDIUM;
        if (request.getSeverity() != null) {
            try {
                severity = ReportSeverity.valueOf(request.getSeverity().toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid severity '{}', using MEDIUM", request.getSeverity());
            }
        }

        // Generate unique report code
        String reportCode = generateReportCode();

        // Build the report
        // Use reportedUserName from request if provided, otherwise use the reported user's full name (if found)
        String displayReportedName = (request.getReportedUserName() != null && !request.getReportedUserName().isBlank())
                ? request.getReportedUserName()
                : (reportedUser != null ? reportedUser.getFullName() : null);

        ViolationReport report = ViolationReport.builder()
                .reportCode(reportCode)
                .title(request.getTitle())
                .reporter(reporter)
                .reportedUser(reportedUser)
                .reportType(reportType)
                .severity(severity)
                .description(request.getDescription())
                .status(ReportStatus.PENDING)
                .reportedUserName(displayReportedName)
                .evidences(new HashSet<>())
                .build();

        // Add evidences
        if (request.getEvidences() != null && !request.getEvidences().isEmpty()) {
            for (CreateViolationReportRequest.EvidenceRequest evidenceReq : request.getEvidences()) {
                EvidenceType evidenceType;
                try {
                    evidenceType = EvidenceType.valueOf(evidenceReq.getEvidenceType().toUpperCase());
                } catch (IllegalArgumentException e) {
                    throw new BadRequestException("Invalid evidence type: " + evidenceReq.getEvidenceType());
                }

                ReportEvidence evidence = ReportEvidence.builder()
                        .violationReport(report)
                        .evidenceType(evidenceType)
                        .fileUrl(evidenceReq.getFileUrl())
                        .fileName(evidenceReq.getFileName())
                        .description(evidenceReq.getDescription())
                        .externalLink(evidenceReq.getExternalLink())
                        .fileSize(evidenceReq.getFileSize())
                        .mimeType(evidenceReq.getMimeType())
                        .build();

                report.getEvidences().add(evidence);
            }
        }

        report = reportRepository.save(report);
        log.info("Created violation report with code: {}", reportCode);

        return ViolationReportResponse.fromEntity(report);
    }

    @Override
    public ViolationReportResponse getReportById(Long reportId, Long userId) {
        ViolationReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new NotFoundException("Report not found with id: " + reportId));

        // User can only view their own reports
        if (!report.getReporter().getId().equals(userId)) {
            throw new ForbiddenException("You can only view your own reports");
        }

        return ViolationReportResponse.fromEntity(report);
    }

    @Override
    public ViolationReportResponse getReportByCode(String reportCode) {
        ViolationReport report = reportRepository.findByReportCode(reportCode)
                .orElseThrow(() -> new NotFoundException("Report not found with code: " + reportCode));
        return ViolationReportResponse.fromEntity(report);
    }

    @Override
    public List<ViolationReportResponse> getReportsByReporter(Long reporterId) {
        return reportRepository.findByReporterIdOrderByCreatedAtDesc(reporterId)
                .stream()
                .map(ViolationReportResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public List<ViolationReportResponse> getReportsAgainstUser(Long reportedUserId) {
        return reportRepository.findByReportedUserIdOrderByCreatedAtDesc(reportedUserId)
                .stream()
                .map(ViolationReportResponse::fromEntity)
                .collect(Collectors.toList());
    }

    // ==================== Admin Operations ====================

    @Override
    public Page<ViolationReportResponse> getAllReports(
            String status,
            String reportType,
            String severity,
            Pageable pageable) {

        ReportStatus reportStatus = null;
        ReportType type = null;
        ReportSeverity sev = null;

        if (status != null) {
            try {
                reportStatus = ReportStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid status: " + status);
            }
        }

        if (reportType != null) {
            try {
                type = ReportType.valueOf(reportType.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid report type: " + reportType);
            }
        }

        if (severity != null) {
            try {
                sev = ReportSeverity.valueOf(severity.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid severity: " + severity);
            }
        }

        return reportRepository.findWithFilters(reportStatus, type, sev, pageable)
                .map(ViolationReportResponse::fromEntity);
    }

    @Override
    public ViolationReportResponse getReportByIdAdmin(Long reportId) {
        ViolationReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new NotFoundException("Report not found with id: " + reportId));
        return ViolationReportResponse.fromEntity(report);
    }

    @Override
    public Page<ViolationReportResponse> getAssignedReports(Long adminId, Pageable pageable) {
        return reportRepository.findByAssignedAdminId(adminId, pageable)
                .map(ViolationReportResponse::fromEntity);
    }

    @Override
    @Transactional
    public ViolationReportResponse updateReport(Long reportId, UpdateViolationReportRequest request) {
        ViolationReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new NotFoundException("Report not found with id: " + reportId));

        if (request.getStatus() != null) {
            try {
                ReportStatus newStatus = ReportStatus.valueOf(request.getStatus().toUpperCase());
                report.setStatus(newStatus);

                if (newStatus == ReportStatus.RESOLVED || newStatus == ReportStatus.DISMISSED) {
                    report.setResolvedAt(LocalDateTime.now());
                }
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid status: " + request.getStatus());
            }
        }

        if (request.getSeverity() != null) {
            try {
                report.setSeverity(ReportSeverity.valueOf(request.getSeverity().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid severity: " + request.getSeverity());
            }
        }

        if (request.getAdminNotes() != null) {
            report.setAdminNotes(request.getAdminNotes());
        }

        if (request.getAssignedAdminId() != null) {
            User admin = userRepository.findById(request.getAssignedAdminId())
                    .orElseThrow(() -> new NotFoundException("Admin not found with id: " + request.getAssignedAdminId()));
            report.setAssignedAdmin(admin);
        }

        if (request.getResolutionAction() != null) {
            try {
                report.setResolutionAction(ResolutionAction.valueOf(request.getResolutionAction().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid resolution action: " + request.getResolutionAction());
            }
        }

        report = reportRepository.save(report);
        log.info("Updated report: {}", report.getReportCode());

        return ViolationReportResponse.fromEntity(report);
    }

    @Override
    @Transactional
    public ViolationReportResponse investigateReport(Long reportId, Long adminId) {
        ViolationReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new NotFoundException("Report not found with id: " + reportId));

        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new NotFoundException("Admin not found with id: " + adminId));

        report.setStatus(ReportStatus.INVESTIGATING);
        report.setAssignedAdmin(admin);

        report = reportRepository.save(report);
        log.info("Admin {} started investigating report: {}", adminId, report.getReportCode());

        return ViolationReportResponse.fromEntity(report);
    }

    @Override
    @Transactional
    public ViolationReportResponse resolveReport(Long reportId, String resolutionAction, String adminNotes) {
        ViolationReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new NotFoundException("Report not found with id: " + reportId));

        ResolutionAction action;
        try {
            action = ResolutionAction.valueOf(resolutionAction.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid resolution action: " + resolutionAction);
        }

        report.setStatus(ReportStatus.RESOLVED);
        report.setResolutionAction(action);
        report.setAdminNotes(adminNotes);
        report.setResolvedAt(LocalDateTime.now());

        report = reportRepository.save(report);
        log.info("Resolved report {} with action: {}", report.getReportCode(), action);

        // Send notifications and emails
        sendReportResolvedNotifications(report);

        return ViolationReportResponse.fromEntity(report);
    }

    @Override
    @Transactional
    public ViolationReportResponse dismissReport(Long reportId, String adminNotes) {
        ViolationReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new NotFoundException("Report not found with id: " + reportId));

        report.setStatus(ReportStatus.DISMISSED);
        report.setResolutionAction(ResolutionAction.NO_ACTION);
        report.setAdminNotes(adminNotes);
        report.setResolvedAt(LocalDateTime.now());

        report = reportRepository.save(report);
        log.info("Dismissed report: {}", report.getReportCode());

        // Send notification to reporter
        sendReportDismissedNotifications(report);

        return ViolationReportResponse.fromEntity(report);
    }

    @Override
    @Transactional
    public ViolationReportResponse escalateReport(Long reportId, String adminNotes) {
        ViolationReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new NotFoundException("Report not found with id: " + reportId));

        report.setResolutionAction(ResolutionAction.ESCALATED);
        report.setSeverity(ReportSeverity.HIGH); // Escalated reports become high severity
        report.setAdminNotes(adminNotes);

        report = reportRepository.save(report);
        log.info("Escalated report: {}", report.getReportCode());

        return ViolationReportResponse.fromEntity(report);
    }

    @Override
    public ViolationReportStatsResponse getReportStats() {
        long total = reportRepository.count();
        long pending = reportRepository.countByStatus(ReportStatus.PENDING);
        long investigating = reportRepository.countByStatus(ReportStatus.INVESTIGATING);
        long resolved = reportRepository.countByStatus(ReportStatus.RESOLVED);
        long dismissed = reportRepository.countByStatus(ReportStatus.DISMISSED);
        long critical = reportRepository.countBySeverity(ReportSeverity.HIGH);

        // Reports this week
        LocalDateTime oneWeekAgo = LocalDateTime.now().minus(7, ChronoUnit.DAYS);
        long thisWeek = reportRepository.countByCreatedAtAfter(oneWeekAgo);

        // Response rate
        double responseRate = total > 0 ? ((double) (resolved + dismissed) / total) * 100 : 0;

        // By type
        Map<String, Long> byType = new HashMap<>();
        for (Object[] row : reportRepository.countByReportType()) {
            byType.put(((ReportType) row[0]).name(), (Long) row[1]);
        }

        // By severity
        Map<String, Long> bySeverity = new HashMap<>();
        for (Object[] row : reportRepository.countBySeverityGrouped()) {
            bySeverity.put(((ReportSeverity) row[0]).name(), (Long) row[1]);
        }

        return ViolationReportStatsResponse.builder()
                .totalReports(total)
                .newReports(pending)
                .investigatingReports(investigating)
                .resolvedReports(resolved)
                .dismissedReports(dismissed)
                .criticalReports(critical)
                .reportsThisWeek(thisWeek)
                .responseRate(Math.round(responseRate * 100.0) / 100.0)
                .reportsByType(byType)
                .reportsBySeverity(bySeverity)
                .build();
    }

    @Override
    @Transactional
    public void deleteReport(Long reportId) {
        ViolationReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new NotFoundException("Report not found with id: " + reportId));

        ReportStatus status = report.getStatus();
        if (status != ReportStatus.RESOLVED && status != ReportStatus.DISMISSED) {
            throw new BadRequestException("Only RESOLVED or DISMISSED reports can be deleted");
        }

        // Delete evidences first
        evidenceRepository.deleteByViolationReportId(reportId);
        reportRepository.delete(report);

        log.info("Deleted report: {}", report.getReportCode());
    }

    @Override
    public List<ViolationReportResponse> getPendingCriticalReports() {
        return reportRepository.findPendingHighSeverityReports()
                .stream()
                .map(ViolationReportResponse::fromEntity)
                .collect(Collectors.toList());
    }

    // ==================== Private Methods ====================

    /**
     * Generate unique report code
     */
    private String generateReportCode() {
        String code;
        do {
            code = "RPT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        } while (reportRepository.existsByReportCode(code));
        return code;
    }

    /**
     * Send notifications when report is resolved
     */
    private void sendReportResolvedNotifications(ViolationReport report) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
            String resolvedDate = report.getResolvedAt().format(formatter);
            
            // Notify reporter
            String reporterTitle = "Báo Cáo Đã Được Giải Quyết";
            String reporterMessage = String.format(
                "Báo cáo #%s của bạn đã được giải quyết. Hành động: %s",
                report.getReportCode(),
                getResolutionActionText(report.getResolutionAction())
            );
            
            notificationService.createNotification(
                report.getReporter().getId(),
                reporterTitle,
                reporterMessage,
                NotificationType.SYSTEM,
                report.getId().toString()
            );
            
            // Send email to reporter
            String reporterEmailContent = buildReportResolvedEmail(
                report.getReporter().getFullName(),
                report.getReportCode(),
                report.getTitle(),
                getResolutionActionText(report.getResolutionAction()),
                report.getAdminNotes(),
                resolvedDate
            );
            
            emailService.sendHtmlEmailAsync(
                report.getReporter().getEmail(),
                "Báo Cáo Vi Phạm Đã Được Giải Quyết - " + report.getReportCode(),
                reporterEmailContent
            );
            
            // If action is WARNING_ISSUED, notify the reported user
            if (report.getResolutionAction() == ResolutionAction.WARNING_ISSUED) {
                if (report.getReportedUser() != null) {
                    String warningTitle = "⚠️ Cảnh Báo Vi Phạm";
                    String warningMessage = String.format(
                        "Bạn đã nhận được cảnh báo về vi phạm: %s. Vui lòng kiểm tra email để biết chi tiết.",
                        report.getTitle()
                    );
                    
                    notificationService.createNotification(
                        report.getReportedUser().getId(),
                        warningTitle,
                        warningMessage,
                        NotificationType.SYSTEM,
                        report.getId().toString()
                    );
                    
                    // Send warning email to reported user
                    if (report.getReportedUser().getEmail() != null && !report.getReportedUser().getEmail().isEmpty()) {
                        String warningEmailContent = buildWarningEmail(
                            report.getReportedUser().getFullName(),
                            report.getReportCode(),
                            report.getTitle(),
                            getReportTypeText(report.getReportType()),
                            report.getAdminNotes(),
                            resolvedDate
                        );
                        
                        emailService.sendHtmlEmailAsync(
                            report.getReportedUser().getEmail(),
                            "Cảnh Báo Vi Phạm Quy Định - SkillVerse",
                            warningEmailContent
                        );
                    } else {
                        log.warn("Cannot send warning email to reported user: email is missing for user {}", 
                            report.getReportedUser().getId());
                    }
                } else {
                    log.warn("Cannot send warning notification: reported user is null for report {}", 
                        report.getReportCode());
                }
            }
            
            log.info("Sent resolution notifications for report: {}", report.getReportCode());
        } catch (Exception e) {
            log.error("Error sending resolution notifications for report {}: {}", 
                report.getReportCode(), e.getMessage());
        }
    }

    /**
     * Send notifications when report is dismissed
     */
    private void sendReportDismissedNotifications(ViolationReport report) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
            String dismissedDate = report.getResolvedAt().format(formatter);
            
            // Notify reporter
            String title = "Báo Cáo Đã Bị Từ Chối";
            String message = String.format(
                "Báo cáo #%s của bạn đã bị từ chối. Vui lòng kiểm tra email để biết thêm chi tiết.",
                report.getReportCode()
            );
            
            notificationService.createNotification(
                report.getReporter().getId(),
                title,
                message,
                NotificationType.SYSTEM,
                report.getId().toString()
            );
            
            // Send email to reporter
            String emailContent = buildReportDismissedEmail(
                report.getReporter().getFullName(),
                report.getReportCode(),
                report.getTitle(),
                report.getAdminNotes(),
                dismissedDate
            );
            
            emailService.sendHtmlEmailAsync(
                report.getReporter().getEmail(),
                "Báo Cáo Vi Phạm Đã Bị Từ Chối - " + report.getReportCode(),
                emailContent
            );
            
            log.info("Sent dismissal notifications for report: {}", report.getReportCode());
        } catch (Exception e) {
            log.error("Error sending dismissal notifications for report {}: {}", 
                report.getReportCode(), e.getMessage());
        }
    }

    /**
     * Build HTML email for resolved report (to reporter)
     */
    private String buildReportResolvedEmail(String userName, String reportCode, 
            String reportTitle, String action, String adminNotes, String resolvedDate) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background: #f0f2f5; margin: 0; padding: 20px; }
                    .container { max-width: 600px; margin: 0 auto; background: white; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 12px rgba(0,0,0,0.1); }
                    .header { background: linear-gradient(135deg, #22c55e 0%%, #16a34a 100%%); color: white; padding: 30px; text-align: center; }
                    .header h1 { margin: 0; font-size: 24px; }
                    .content { padding: 30px; }
                    .info-box { background: #f8fafc; border-left: 4px solid #22c55e; padding: 15px; margin: 20px 0; border-radius: 6px; }
                    .info-row { display: flex; justify-content: space-between; padding: 8px 0; border-bottom: 1px solid #e2e8f0; }
                    .info-row:last-child { border-bottom: none; }
                    .label { font-weight: 600; color: #475569; }
                    .value { color: #1e293b; }
                    .notes { background: #fefce8; border-left: 4px solid #eab308; padding: 15px; margin: 20px 0; border-radius: 6px; }
                    .footer { background: #f8fafc; padding: 20px; text-align: center; color: #64748b; font-size: 14px; }
                    .button { display: inline-block; background: #22c55e; color: white; padding: 12px 24px; text-decoration: none; border-radius: 8px; margin-top: 20px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>✅ Báo Cáo Đã Được Giải Quyết</h1>
                    </div>
                    <div class="content">
                        <p>Xin chào <strong>%s</strong>,</p>
                        <p>Báo cáo vi phạm của bạn đã được xem xét và giải quyết bởi đội ngũ quản trị SkillVerse.</p>
                        
                        <div class="info-box">
                            <div class="info-row">
                                <span class="label">Mã báo cáo:</span>
                                <span class="value">%s</span>
                            </div>
                            <div class="info-row">
                                <span class="label">Tiêu đề:</span>
                                <span class="value">%s</span>
                            </div>
                            <div class="info-row">
                                <span class="label">Hành động:</span>
                                <span class="value">%s</span>
                            </div>
                            <div class="info-row">
                                <span class="label">Thời gian:</span>
                                <span class="value">%s</span>
                            </div>
                        </div>
                        
                        <div class="notes">
                            <strong>Ghi chú từ Admin:</strong>
                            <p style="margin: 10px 0 0;">%s</p>
                        </div>
                        
                        <p>Cảm ơn bạn đã đóng góp vào việc duy trì một cộng đồng an toàn và lành mạnh.</p>
                    </div>
                    <div class="footer">
                        <p>&copy; 2026 SkillVerse - Nền tảng học tập và phát triển kỹ năng</p>
                        <p>Email này được gửi tự động, vui lòng không trả lời.</p>
                    </div>
                </div>
            </body>
            </html>
            """, userName, reportCode, reportTitle, action, resolvedDate, adminNotes);
    }

    /**
     * Build HTML email for warning (to reported user)
     */
    private String buildWarningEmail(String userName, String reportCode, 
            String reportTitle, String reportType, String warningMessage, String date) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background: #f0f2f5; margin: 0; padding: 20px; }
                    .container { max-width: 600px; margin: 0 auto; background: white; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 12px rgba(0,0,0,0.1); }
                    .header { background: linear-gradient(135deg, #eab308 0%%, #ca8a04 100%%); color: white; padding: 30px; text-align: center; }
                    .header h1 { margin: 0; font-size: 24px; }
                    .content { padding: 30px; }
                    .warning-box { background: #fef3c7; border-left: 4px solid #eab308; padding: 20px; margin: 20px 0; border-radius: 6px; }
                    .info-box { background: #f8fafc; padding: 15px; margin: 20px 0; border-radius: 6px; }
                    .info-row { display: flex; justify-content: space-between; padding: 8px 0; border-bottom: 1px solid #e2e8f0; }
                    .info-row:last-child { border-bottom: none; }
                    .label { font-weight: 600; color: #475569; }
                    .value { color: #1e293b; }
                    .important { background: #fee2e2; border-left: 4px solid #ef4444; padding: 15px; margin: 20px 0; border-radius: 6px; color: #991b1b; }
                    .footer { background: #f8fafc; padding: 20px; text-align: center; color: #64748b; font-size: 14px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>⚠️ Cảnh Báo Vi Phạm</h1>
                    </div>
                    <div class="content">
                        <p>Xin chào <strong>%s</strong>,</p>
                        <p>Bạn nhận được cảnh báo này vì hành vi của bạn đã vi phạm các quy định của SkillVerse.</p>
                        
                        <div class="warning-box">
                            <strong style="font-size: 16px;">Chi tiết cảnh báo:</strong>
                            <div class="info-box" style="margin-top: 15px;">
                                <div class="info-row">
                                    <span class="label">Mã báo cáo:</span>
                                    <span class="value">%s</span>
                                </div>
                                <div class="info-row">
                                    <span class="label">Loại vi phạm:</span>
                                    <span class="value">%s</span>
                                </div>
                                <div class="info-row">
                                    <span class="label">Nội dung:</span>
                                    <span class="value">%s</span>
                                </div>
                                <div class="info-row">
                                    <span class="label">Thời gian:</span>
                                    <span class="value">%s</span>
                                </div>
                            </div>
                        </div>
                        
                        <div style="background: #fff7ed; border-left: 4px solid #f97316; padding: 15px; margin: 20px 0; border-radius: 6px;">
                            <strong>Lời nhắn từ đội ngũ quản trị:</strong>
                            <p style="margin: 10px 0 0;">%s</p>
                        </div>
                        
                        <div class="important">
                            <strong>⚠️ Lưu ý quan trọng:</strong>
                            <ul style="margin: 10px 0; padding-left: 20px;">
                                <li>Đây là cảnh báo chính thức từ SkillVerse</li>
                                <li>Nếu tiếp tục vi phạm, tài khoản của bạn có thể bị tạm khóa hoặc cấm vĩnh viễn</li>
                                <li>Vui lòng tuân thủ quy định cộng đồng để tránh các hành động xử lý tiếp theo</li>
                            </ul>
                        </div>
                        
                        <p>Nếu bạn có bất kỳ thắc mắc nào, vui lòng liên hệ với đội ngũ hỗ trợ của chúng tôi.</p>
                    </div>
                    <div class="footer">
                        <p>&copy; 2026 SkillVerse - Nền tảng học tập và phát triển kỹ năng</p>
                        <p>Email này được gửi tự động, vui lòng không trả lời.</p>
                    </div>
                </div>
            </body>
            </html>
            """, userName, reportCode, reportType, reportTitle, date, warningMessage);
    }

    /**
     * Build HTML email for dismissed report (to reporter)
     */
    private String buildReportDismissedEmail(String userName, String reportCode, 
            String reportTitle, String reason, String dismissedDate) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background: #f0f2f5; margin: 0; padding: 20px; }
                    .container { max-width: 600px; margin: 0 auto; background: white; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 12px rgba(0,0,0,0.1); }
                    .header { background: linear-gradient(135deg, #64748b 0%%, #475569 100%%); color: white; padding: 30px; text-align: center; }
                    .header h1 { margin: 0; font-size: 24px; }
                    .content { padding: 30px; }
                    .info-box { background: #f8fafc; border-left: 4px solid #64748b; padding: 15px; margin: 20px 0; border-radius: 6px; }
                    .info-row { display: flex; justify-content: space-between; padding: 8px 0; border-bottom: 1px solid #e2e8f0; }
                    .info-row:last-child { border-bottom: none; }
                    .label { font-weight: 600; color: #475569; }
                    .value { color: #1e293b; }
                    .reason { background: #fef3c7; border-left: 4px solid #eab308; padding: 15px; margin: 20px 0; border-radius: 6px; }
                    .footer { background: #f8fafc; padding: 20px; text-align: center; color: #64748b; font-size: 14px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>Báo Cáo Đã Bị Từ Chối</h1>
                    </div>
                    <div class="content">
                        <p>Xin chào <strong>%s</strong>,</p>
                        <p>Sau khi xem xét kỹ lưỡng, đội ngũ quản trị SkillVerse quyết định không tiến hành xử lý báo cáo của bạn.</p>
                        
                        <div class="info-box">
                            <div class="info-row">
                                <span class="label">Mã báo cáo:</span>
                                <span class="value">%s</span>
                            </div>
                            <div class="info-row">
                                <span class="label">Tiêu đề:</span>
                                <span class="value">%s</span>
                            </div>
                            <div class="info-row">
                                <span class="label">Thời gian:</span>
                                <span class="value">%s</span>
                            </div>
                        </div>
                        
                        <div class="reason">
                            <strong>Lý do từ chối:</strong>
                            <p style="margin: 10px 0 0;">%s</p>
                        </div>
                        
                        <p>Chúng tôi rất trân trọng sự đóng góp của bạn trong việc duy trì một cộng đồng an toàn. Nếu bạn có thêm bằng chứng hoặc thông tin mới, vui lòng gửi báo cáo mới.</p>
                        
                        <p>Nếu bạn không đồng ý với quyết định này, vui lòng liên hệ với đội ngũ hỗ trợ của chúng tôi để được giải đáp thêm.</p>
                    </div>
                    <div class="footer">
                        <p>&copy; 2026 SkillVerse - Nền tảng học tập và phát triển kỹ năng</p>
                        <p>Email này được gửi tự động, vui lòng không trả lời.</p>
                    </div>
                </div>
            </body>
            </html>
            """, userName, reportCode, reportTitle, dismissedDate, reason);
    }

    private String getResolutionActionText(ResolutionAction action) {
        return switch (action) {
            case NO_ACTION -> "Không có hành động";
            case WARNING_ISSUED -> "Đã gửi cảnh báo";
            case CONTENT_REMOVED -> "Đã xóa nội dung";
            case ACCOUNT_SUSPENDED -> "Tạm khóa tài khoản";
            case ACCOUNT_BANNED -> "Cấm tài khoản vĩnh viễn";
            case ESCALATED -> "Leo thang";
        };
    }

    private String getReportTypeText(ReportType type) {
        return switch (type) {
            case INAPPROPRIATE_CONTENT -> "Nội dung không phù hợp";
            case HARASSMENT -> "Quấy rối";
            case SPAM -> "Spam và quảng cáo trái phép";
            case FRAUD -> "Lừa đảo tài chính";
            case COPYRIGHT_VIOLATION -> "Vi phạm bản quyền";
            case HATE_SPEECH -> "Phát ngôn thù hận";
            case IMPERSONATION -> "Mạo danh";
            case MISINFORMATION -> "Thông tin sai lệch";
            case PRIVACY_VIOLATION -> "Vi phạm quyền riêng tư";
            case OTHER -> "Khác";
        };
    }
}
