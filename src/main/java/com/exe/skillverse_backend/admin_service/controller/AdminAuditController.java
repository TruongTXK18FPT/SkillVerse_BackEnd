package com.exe.skillverse_backend.admin_service.controller;

import com.exe.skillverse_backend.admin_service.dto.response.AuditLogDto;
import com.exe.skillverse_backend.admin_service.dto.response.AuditSummaryResponseDto;
import com.exe.skillverse_backend.shared.entity.AuditLog;
import com.exe.skillverse_backend.shared.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/admin/audit")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Audit Management", description = "Admin endpoints for viewing and managing audit logs")
@SecurityRequirement(name = "Bearer Authentication")
public class AdminAuditController {

        private final AuditService auditService;

        @GetMapping("/recent")
        @Operation(summary = "Get Recent Audit Logs", description = "Retrieve the most recent 100 audit log entries")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Recent audit logs retrieved successfully"),
                        @ApiResponse(responseCode = "403", description = "Access denied - Admin role required")
        })
        @PreAuthorize("hasRole('ADMIN')")
        public ResponseEntity<List<AuditLogDto>> getRecentAuditLogs() {
                log.info("Admin requesting recent audit logs");
                List<AuditLogDto> auditLogs = auditService.getRecentAuditLogsDto();
                return ResponseEntity.ok(auditLogs);
        }

        @GetMapping("/user/{userId}")
        @Operation(summary = "Get User Audit Logs", description = "Retrieve all audit logs for a specific user")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "User audit logs retrieved successfully"),
                        @ApiResponse(responseCode = "403", description = "Access denied - Admin role required"),
                        @ApiResponse(responseCode = "404", description = "User not found")
        })
        @PreAuthorize("hasRole('ADMIN')")
        public ResponseEntity<List<AuditLogDto>> getUserAuditLogs(
                        @Parameter(description = "User ID to get audit logs for", required = true) @PathVariable Long userId) {
                log.info("Admin requesting audit logs for user: {}", userId);
                List<AuditLogDto> auditLogs = auditService.getUserAuditLogsDto(userId);
                return ResponseEntity.ok(auditLogs);
        }

        @GetMapping("/object/{objectType}/{objectId}")
        @Operation(summary = "Get Object Audit Logs", description = "Retrieve all audit logs for a specific object")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Object audit logs retrieved successfully"),
                        @ApiResponse(responseCode = "403", description = "Access denied - Admin role required")
        })
        @PreAuthorize("hasRole('ADMIN')")
        public ResponseEntity<List<AuditLogDto>> getObjectAuditLogs(
                        @Parameter(description = "Type of object (e.g., USER, COURSE, MENTOR)", required = true) @PathVariable String objectType,
                        @Parameter(description = "ID of the object", required = true) @PathVariable Long objectId) {
                log.info("Admin requesting audit logs for object: {} with ID: {}", objectType, objectId);
                List<AuditLogDto> auditLogs = auditService.getObjectAuditLogsDto(objectType, objectId);
                return ResponseEntity.ok(auditLogs);
        }

        @GetMapping("/action/{action}")
        @Operation(summary = "Get Audit Logs by Action", description = "Retrieve all audit logs for a specific action type")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Action audit logs retrieved successfully"),
                        @ApiResponse(responseCode = "403", description = "Access denied - Admin role required")
        })
        @PreAuthorize("hasRole('ADMIN')")
        public ResponseEntity<List<AuditLogDto>> getAuditLogsByAction(
                        @Parameter(description = "Action type (e.g., LOGIN, LOGOUT, CREATE, UPDATE, DELETE)", required = true) @PathVariable String action) {
                log.info("Admin requesting audit logs for action: {}", action);
                List<AuditLogDto> auditLogs = auditService.getAuditLogsByActionDto(action);
                return ResponseEntity.ok(auditLogs);
        }

        @GetMapping("/date-range")
        @Operation(summary = "Get Audit Logs by Date Range", description = "Retrieve audit logs within a specific date range")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Date range audit logs retrieved successfully"),
                        @ApiResponse(responseCode = "400", description = "Invalid date format or range"),
                        @ApiResponse(responseCode = "403", description = "Access denied - Admin role required")
        })
        @PreAuthorize("hasRole('ADMIN')")
        public ResponseEntity<List<AuditLogDto>> getAuditLogsByDateRange(
                        @Parameter(description = "Start date (ISO format: 2024-01-01T00:00:00)", required = true) @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
                        @Parameter(description = "End date (ISO format: 2024-12-31T23:59:59)", required = true) @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
                log.info("Admin requesting audit logs from {} to {}", startDate, endDate);

                if (startDate.isAfter(endDate)) {
                        return ResponseEntity.badRequest().build();
                }

                List<AuditLogDto> auditLogs = auditService.getAuditLogsByDateRangeDto(startDate, endDate);
                return ResponseEntity.ok(auditLogs);
        }

        @GetMapping("/summary")
        @Operation(summary = "Get Audit Summary", description = "Get a summary of audit activities")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Audit summary retrieved successfully"),
                        @ApiResponse(responseCode = "403", description = "Access denied - Admin role required")
        })
        @PreAuthorize("hasRole('ADMIN')")
        public ResponseEntity<AuditSummaryResponseDto> getAuditSummary() {
                log.info("Admin requesting audit summary");

                List<AuditLog> recentLogs = auditService.getRecentAuditLogs();
                long totalLogs = recentLogs.size();
                long userRegistrations = recentLogs.stream()
                                .filter(log -> "USER_REGISTRATION".equals(log.getAction()))
                                .count();
                long loginAttempts = recentLogs.stream()
                                .filter(log -> "LOGIN".equals(log.getAction())
                                                || "LOGIN_SUCCESS".equals(log.getAction())
                                                || "LOGIN_FAILED".equals(log.getAction()))
                                .count();
                long mentorApplications = recentLogs.stream()
                                .filter(log -> "MENTOR_REGISTRATION".equals(log.getAction()))
                                .count();
                long businessRegistrations = recentLogs.stream()
                                .filter(log -> "RECRUITER_REGISTRATION".equals(log.getAction()))
                                .count();

                AuditSummaryResponseDto summary = AuditSummaryResponseDto.builder()
                                .totalRecentLogs(totalLogs)
                                .userRegistrations(userRegistrations)
                                .loginAttempts(loginAttempts)
                                .mentorApplications(mentorApplications)
                                .businessRegistrations(businessRegistrations)
                                .build();

                return ResponseEntity.ok(summary);
        }

}