package com.exe.skillverse_backend.report_service;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.report_service.dto.request.CreateViolationReportRequest;
import com.exe.skillverse_backend.report_service.dto.request.UpdateViolationReportRequest;
import com.exe.skillverse_backend.report_service.dto.response.ViolationReportResponse;
import com.exe.skillverse_backend.report_service.dto.response.ViolationReportStatsResponse;
import com.exe.skillverse_backend.report_service.entity.ViolationReport;
import com.exe.skillverse_backend.report_service.entity.ViolationReport.ReportSeverity;
import com.exe.skillverse_backend.report_service.entity.ViolationReport.ReportStatus;
import com.exe.skillverse_backend.report_service.entity.ViolationReport.ReportType;
import com.exe.skillverse_backend.report_service.entity.ViolationReport.ResolutionAction;
import com.exe.skillverse_backend.report_service.repository.ReportEvidenceRepository;
import com.exe.skillverse_backend.report_service.repository.ViolationReportRepository;
import com.exe.skillverse_backend.report_service.service.impl.ViolationReportServiceImpl;
import com.exe.skillverse_backend.shared.exception.BadRequestException;
import com.exe.skillverse_backend.shared.exception.ForbiddenException;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ViolationReportServiceImpl
 */
@ExtendWith(MockitoExtension.class)
class ViolationReportServiceImplTest {

    @Mock
    private ViolationReportRepository reportRepository;

    @Mock
    private ReportEvidenceRepository evidenceRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ViolationReportServiceImpl reportService;

    private User reporter;
    private User reportedUser;
    private User admin;
    private ViolationReport mockReport;
    private CreateViolationReportRequest createRequest;

    @BeforeEach
    void setUp() {
        // Setup mock users
        reporter = User.builder()
                .id(1L)
                .email("reporter@test.com")
                .firstName("Reporter")
                .lastName("User")
                .build();

        reportedUser = User.builder()
                .id(2L)
                .email("reported@test.com")
                .firstName("Reported")
                .lastName("User")
                .build();

        admin = User.builder()
                .id(3L)
                .email("admin@test.com")
                .firstName("Admin")
                .lastName("User")
                .build();

        // Setup mock report
        mockReport = ViolationReport.builder()
                .id(1L)
                .reportCode("RPT-TEST1234")
                .title("Test Report")
                .reporter(reporter)
                .reportedUser(reportedUser)
                .reportType(ReportType.HARASSMENT)
                .severity(ReportSeverity.HIGH)
                .description("This is a test violation report description with enough characters.")
                .status(ReportStatus.PENDING)
                .evidences(new HashSet<>())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // Setup create request
        createRequest = CreateViolationReportRequest.builder()
                .title("Test Report")
                .reportedUserId(2L)
                .reportType("HARASSMENT")
                .severity("HIGH")
                .description("This is a test violation report description with enough characters.")
                .build();
    }

    @Nested
    @DisplayName("Create Report Tests")
    class CreateReportTests {

        @Test
        @DisplayName("Should create report successfully")
        void shouldCreateReportSuccessfully() {
            // Arrange
            when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));
            when(userRepository.findById(2L)).thenReturn(Optional.of(reportedUser));
            when(reportRepository.existsPendingReport(anyLong(), anyLong(), any())).thenReturn(false);
            when(reportRepository.existsByReportCode(anyString())).thenReturn(false);
            when(reportRepository.save(any(ViolationReport.class))).thenReturn(mockReport);

            // Act
            ViolationReportResponse response = reportService.createReport(1L, createRequest);

            // Assert
            assertNotNull(response);
            assertEquals("Test Report", response.getTitle());
            assertEquals("HARASSMENT", response.getReportType());
            assertEquals("HIGH", response.getSeverity());
            verify(reportRepository).save(any(ViolationReport.class));
        }

        @Test
        @DisplayName("Should throw NotFoundException when reporter not found")
        void shouldThrowNotFoundExceptionWhenReporterNotFound() {
            // Arrange
            when(userRepository.findById(1L)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(NotFoundException.class, () -> reportService.createReport(1L, createRequest));
        }

        @Test
        @DisplayName("Should throw NotFoundException when reported user not found")
        void shouldThrowNotFoundExceptionWhenReportedUserNotFound() {
            // Arrange
            when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));
            when(userRepository.findById(2L)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(NotFoundException.class, () -> reportService.createReport(1L, createRequest));
        }

        @Test
        @DisplayName("Should throw BadRequestException when self-reporting")
        void shouldThrowBadRequestExceptionWhenSelfReporting() {
            // Arrange
            when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));
            createRequest.setReportedUserId(1L);
            when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));

            // Act & Assert
            assertThrows(BadRequestException.class, () -> reportService.createReport(1L, createRequest));
        }

        @Test
        @DisplayName("Should throw BadRequestException for duplicate pending report")
        void shouldThrowBadRequestExceptionForDuplicatePendingReport() {
            // Arrange
            when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));
            when(userRepository.findById(2L)).thenReturn(Optional.of(reportedUser));
            when(reportRepository.existsPendingReport(1L, 2L, ReportType.HARASSMENT)).thenReturn(true);

            // Act & Assert
            assertThrows(BadRequestException.class, () -> reportService.createReport(1L, createRequest));
        }

        @Test
        @DisplayName("Should throw BadRequestException for invalid report type")
        void shouldThrowBadRequestExceptionForInvalidReportType() {
            // Arrange
            when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));
            when(userRepository.findById(2L)).thenReturn(Optional.of(reportedUser));
            createRequest.setReportType("INVALID_TYPE");

            // Act & Assert
            assertThrows(BadRequestException.class, () -> reportService.createReport(1L, createRequest));
        }
    }

    @Nested
    @DisplayName("Get Report Tests")
    class GetReportTests {

        @Test
        @DisplayName("Should get report by ID for owner")
        void shouldGetReportByIdForOwner() {
            // Arrange
            when(reportRepository.findById(1L)).thenReturn(Optional.of(mockReport));

            // Act
            ViolationReportResponse response = reportService.getReportById(1L, 1L);

            // Assert
            assertNotNull(response);
            assertEquals(1L, response.getId());
        }

        @Test
        @DisplayName("Should throw ForbiddenException when non-owner tries to access")
        void shouldThrowForbiddenExceptionWhenNonOwnerTriesToAccess() {
            // Arrange
            when(reportRepository.findById(1L)).thenReturn(Optional.of(mockReport));

            // Act & Assert
            assertThrows(ForbiddenException.class, () -> reportService.getReportById(1L, 999L));
        }

        @Test
        @DisplayName("Should get report by code")
        void shouldGetReportByCode() {
            // Arrange
            when(reportRepository.findByReportCode("RPT-TEST1234")).thenReturn(Optional.of(mockReport));

            // Act
            ViolationReportResponse response = reportService.getReportByCode("RPT-TEST1234");

            // Assert
            assertNotNull(response);
            assertEquals("RPT-TEST1234", response.getReportCode());
        }

        @Test
        @DisplayName("Should throw NotFoundException when report code not found")
        void shouldThrowNotFoundExceptionWhenReportCodeNotFound() {
            // Arrange
            when(reportRepository.findByReportCode(anyString())).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(NotFoundException.class, () -> reportService.getReportByCode("INVALID"));
        }

        @Test
        @DisplayName("Should get reports by reporter ID")
        void shouldGetReportsByReporterId() {
            // Arrange
            when(reportRepository.findByReporterIdOrderByCreatedAtDesc(1L))
                    .thenReturn(Arrays.asList(mockReport));

            // Act
            List<ViolationReportResponse> reports = reportService.getReportsByReporter(1L);

            // Assert
            assertEquals(1, reports.size());
        }

        @Test
        @DisplayName("Should get reports by ID for admin without ownership check")
        void shouldGetReportByIdForAdminWithoutOwnershipCheck() {
            // Arrange
            when(reportRepository.findById(1L)).thenReturn(Optional.of(mockReport));

            // Act
            ViolationReportResponse response = reportService.getReportByIdAdmin(1L);

            // Assert
            assertNotNull(response);
            assertEquals(1L, response.getId());
        }
    }

    @Nested
    @DisplayName("Admin Operations Tests")
    class AdminOperationsTests {

        @Test
        @DisplayName("Should get all reports with filters")
        void shouldGetAllReportsWithFilters() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 20);
            Page<ViolationReport> page = new PageImpl<>(Arrays.asList(mockReport));
            when(reportRepository.findWithFilters(any(), any(), any(), any())).thenReturn(page);

            // Act
            Page<ViolationReportResponse> result = reportService.getAllReports(
                    "PENDING", "HARASSMENT", "HIGH", pageable);

            // Assert
            assertEquals(1, result.getTotalElements());
        }

        @Test
        @DisplayName("Should investigate report")
        void shouldInvestigateReport() {
            // Arrange
            when(reportRepository.findById(1L)).thenReturn(Optional.of(mockReport));
            when(userRepository.findById(3L)).thenReturn(Optional.of(admin));
            when(reportRepository.save(any(ViolationReport.class))).thenReturn(mockReport);

            // Act
            ViolationReportResponse response = reportService.investigateReport(1L, 3L);

            // Assert
            assertNotNull(response);
            verify(reportRepository).save(any(ViolationReport.class));
        }

        @Test
        @DisplayName("Should resolve report with action")
        void shouldResolveReportWithAction() {
            // Arrange
            when(reportRepository.findById(1L)).thenReturn(Optional.of(mockReport));
            when(reportRepository.save(any(ViolationReport.class))).thenAnswer(invocation -> {
                ViolationReport saved = invocation.getArgument(0);
                saved.setStatus(ReportStatus.RESOLVED);
                saved.setResolutionAction(ResolutionAction.WARNING_ISSUED);
                return saved;
            });

            // Act
            ViolationReportResponse response = reportService.resolveReport(
                    1L, "WARNING_ISSUED", "User has been warned");

            // Assert
            assertNotNull(response);
            verify(reportRepository).save(any(ViolationReport.class));
        }

        @Test
        @DisplayName("Should dismiss report")
        void shouldDismissReport() {
            // Arrange
            when(reportRepository.findById(1L)).thenReturn(Optional.of(mockReport));
            when(reportRepository.save(any(ViolationReport.class))).thenReturn(mockReport);

            // Act
            ViolationReportResponse response = reportService.dismissReport(1L, "Not enough evidence");

            // Assert
            assertNotNull(response);
            verify(reportRepository).save(any(ViolationReport.class));
        }

        @Test
        @DisplayName("Should escalate report")
        void shouldEscalateReport() {
            // Arrange
            when(reportRepository.findById(1L)).thenReturn(Optional.of(mockReport));
            when(reportRepository.save(any(ViolationReport.class))).thenReturn(mockReport);

            // Act
            ViolationReportResponse response = reportService.escalateReport(1L, "Needs higher authority review");

            // Assert
            assertNotNull(response);
            verify(reportRepository).save(any(ViolationReport.class));
        }

        @Test
        @DisplayName("Should update report")
        void shouldUpdateReport() {
            // Arrange
            UpdateViolationReportRequest updateRequest = UpdateViolationReportRequest.builder()
                    .status("INVESTIGATING")
                    .severity("HIGH")
                    .adminNotes("Under review")
                    .build();

            when(reportRepository.findById(1L)).thenReturn(Optional.of(mockReport));
            when(reportRepository.save(any(ViolationReport.class))).thenReturn(mockReport);

            // Act
            ViolationReportResponse response = reportService.updateReport(1L, updateRequest);

            // Assert
            assertNotNull(response);
            verify(reportRepository).save(any(ViolationReport.class));
        }
    }

    @Nested
    @DisplayName("Delete Report Tests")
    class DeleteReportTests {

        @Test
        @DisplayName("Should delete resolved report")
        void shouldDeleteResolvedReport() {
            // Arrange
            mockReport.setStatus(ReportStatus.RESOLVED);
            when(reportRepository.findById(1L)).thenReturn(Optional.of(mockReport));

            // Act
            reportService.deleteReport(1L);

            // Assert
            verify(evidenceRepository).deleteByViolationReportId(1L);
            verify(reportRepository).delete(mockReport);
        }

        @Test
        @DisplayName("Should delete dismissed report")
        void shouldDeleteDismissedReport() {
            // Arrange
            mockReport.setStatus(ReportStatus.DISMISSED);
            when(reportRepository.findById(1L)).thenReturn(Optional.of(mockReport));

            // Act
            reportService.deleteReport(1L);

            // Assert
            verify(evidenceRepository).deleteByViolationReportId(1L);
            verify(reportRepository).delete(mockReport);
        }

        @Test
        @DisplayName("Should throw BadRequestException when deleting pending report")
        void shouldThrowBadRequestExceptionWhenDeletingPendingReport() {
            // Arrange
            mockReport.setStatus(ReportStatus.PENDING);
            when(reportRepository.findById(1L)).thenReturn(Optional.of(mockReport));

            // Act & Assert
            assertThrows(BadRequestException.class, () -> reportService.deleteReport(1L));
        }
    }

    @Nested
    @DisplayName("Statistics Tests")
    class StatisticsTests {

        @Test
        @DisplayName("Should get report statistics")
        void shouldGetReportStatistics() {
            // Arrange
            when(reportRepository.count()).thenReturn(100L);
            when(reportRepository.countByStatus(ReportStatus.PENDING)).thenReturn(10L);
            when(reportRepository.countByStatus(ReportStatus.INVESTIGATING)).thenReturn(5L);
            when(reportRepository.countByStatus(ReportStatus.RESOLVED)).thenReturn(70L);
            when(reportRepository.countByStatus(ReportStatus.DISMISSED)).thenReturn(15L);
            when(reportRepository.countBySeverity(ReportSeverity.HIGH)).thenReturn(20L);
            when(reportRepository.countByCreatedAtAfter(any())).thenReturn(12L);
            when(reportRepository.countByReportType()).thenReturn(Arrays.asList(
                    new Object[]{ReportType.HARASSMENT, 30L},
                    new Object[]{ReportType.SPAM, 25L}
            ));
            when(reportRepository.countBySeverityGrouped()).thenReturn(Arrays.asList(
                    new Object[]{ReportSeverity.HIGH, 20L},
                    new Object[]{ReportSeverity.MEDIUM, 50L},
                    new Object[]{ReportSeverity.LOW, 30L}
            ));

            // Act
            ViolationReportStatsResponse stats = reportService.getReportStats();

            // Assert
            assertNotNull(stats);
            assertEquals(100L, stats.getTotalReports());
            assertEquals(10L, stats.getNewReports());
            assertEquals(5L, stats.getInvestigatingReports());
            assertEquals(70L, stats.getResolvedReports());
            assertEquals(15L, stats.getDismissedReports());
            assertEquals(20L, stats.getCriticalReports());
            assertEquals(12L, stats.getReportsThisWeek());
            assertEquals(85.0, stats.getResponseRate()); // (70+15)/100 * 100
        }

        @Test
        @DisplayName("Should get pending critical reports")
        void shouldGetPendingCriticalReports() {
            // Arrange
            when(reportRepository.findPendingHighSeverityReports())
                    .thenReturn(Arrays.asList(mockReport));

            // Act
            List<ViolationReportResponse> reports = reportService.getPendingCriticalReports();

            // Assert
            assertEquals(1, reports.size());
        }
    }
}
