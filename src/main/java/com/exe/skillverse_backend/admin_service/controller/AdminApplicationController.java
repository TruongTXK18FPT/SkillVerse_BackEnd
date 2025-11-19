package com.exe.skillverse_backend.admin_service.controller;

import com.exe.skillverse_backend.admin_service.dto.request.ApplicationActionRequest;
import com.exe.skillverse_backend.admin_service.dto.response.AdminApprovalResponse;
import com.exe.skillverse_backend.admin_service.dto.response.ApplicationsResponse;
import com.exe.skillverse_backend.admin_service.service.AdminApprovalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Application Management", description = "Unified admin endpoints for managing mentor/recruiter applications")
@SecurityRequirement(name = "Bearer Authentication")
public class AdminApplicationController {

    private final AdminApprovalService adminApprovalService;

    @PostMapping("/applications/process")
    @Operation(summary = "Process Application (Unified)", description = "Unified API to approve or reject mentor/recruiter applications with a single endpoint")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Application processed successfully", content = @Content(schema = @Schema(implementation = AdminApprovalResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request or processing failed", content = @Content),
            @ApiResponse(responseCode = "403", description = "Access denied - admin role required", content = @Content)
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminApprovalResponse> processApplication(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ApplicationActionRequest request) {
        try {
            log.info("Processing {} action for {} application, userId: {}",
                    request.getAction(), request.getApplicationType(), request.getUserId());

            // Extract admin ID from JWT
            Long adminId = Long.parseLong(jwt.getSubject());

            AdminApprovalResponse response = adminApprovalService.processApplication(request, adminId);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.warn("Invalid application processing request: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(AdminApprovalResponse.builder()
                            .success(false)
                            .message("Invalid request: " + e.getMessage())
                            .build());
        } catch (Exception e) {
            log.error("Error processing application: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(AdminApprovalResponse.builder()
                            .success(false)
                            .message("Processing failed: " + e.getMessage())
                            .build());
        }
    }

    @GetMapping("/applications")
    @Operation(summary = "Get Applications with Status Filter", description = "Get mentor and recruiter applications with optional status filtering (PENDING, APPROVED, REJECTED, ALL)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Applications retrieved successfully", content = @Content(schema = @Schema(implementation = ApplicationsResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid status filter", content = @Content),
            @ApiResponse(responseCode = "403", description = "Access denied - admin role required", content = @Content)
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApplicationsResponse> getApplications(
            @Parameter(description = "Status filter: PENDING, APPROVED, REJECTED, or ALL (default)", example = "PENDING") @RequestParam(defaultValue = "ALL") String status) {
        try {
            log.info("Admin requesting applications with status filter: {}", status);

            ApplicationsResponse response = adminApprovalService.getApplications(status);

            log.info("Retrieved {} total applications with filter: {}",
                    response.getTotalApplications(), response.getFilterStatus());
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.warn("Invalid status filter: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error retrieving applications", e);
            return ResponseEntity.badRequest().build();
        }
    }
}