package com.exe.skillverse_backend.admin_service.controller;

import com.exe.skillverse_backend.auth_service.dto.request.AdminApprovalRequest;
import com.exe.skillverse_backend.admin_service.dto.response.AdminApprovalResponse;
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
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Approval", description = "Admin endpoints for approving/rejecting applications")
@SecurityRequirement(name = "bearerAuth")
public class AdminApprovalController {

    private final AdminApprovalService adminApprovalService;

    @GetMapping("/pending-mentors")
    @Operation(summary = "Get Pending Mentor Applications", description = "Retrieve all mentor applications pending approval")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getPendingMentorApplications() {
        try {
            var applications = adminApprovalService.getPendingMentorApplications();
            return ResponseEntity.ok(applications);
        } catch (Exception e) {
            log.error("Error fetching pending mentor applications", e);
            return ResponseEntity.badRequest().body("Error fetching applications: " + e.getMessage());
        }
    }

    @GetMapping("/pending-recruiters")
    @Operation(summary = "Get Pending Recruiter Applications", description = "Retrieve all recruiter applications pending approval")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getPendingRecruiterApplications() {
        try {
            var applications = adminApprovalService.getPendingRecruiterApplications();
            return ResponseEntity.ok(applications);
        } catch (Exception e) {
            log.error("Error fetching pending recruiter applications", e);
            return ResponseEntity.badRequest().body("Error fetching applications: " + e.getMessage());
        }
    }

    @PutMapping("/approve/mentor/{userId}")
    @Operation(summary = "Approve Mentor Application", description = "Approve a mentor application by user ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Mentor approved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request or approval failed"),
            @ApiResponse(responseCode = "404", description = "Mentor application not found")
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminApprovalResponse> approveMentor(
            @Parameter(description = "User ID of the mentor to approve") @PathVariable Long userId) {
        try {
            log.info("Admin approving mentor application for user ID: {}", userId);
            AdminApprovalResponse response = adminApprovalService.approveMentor(userId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error approving mentor for user ID: {}", userId, e);
            return ResponseEntity.badRequest()
                    .body(AdminApprovalResponse.builder()
                            .success(false)
                            .message(e.getMessage())
                            .build());
        }
    }

    @PutMapping("/approve/recruiter/{userId}")
    @Operation(summary = "Approve Recruiter Application", description = "Approve a recruiter application by user ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Recruiter approved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request or approval failed"),
            @ApiResponse(responseCode = "404", description = "Recruiter application not found")
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminApprovalResponse> approveRecruiter(
            @Parameter(description = "User ID of the recruiter to approve") @PathVariable Long userId) {
        try {
            log.info("Admin approving recruiter application for user ID: {}", userId);
            AdminApprovalResponse response = adminApprovalService.approveRecruiter(userId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error approving recruiter for user ID: {}", userId, e);
            return ResponseEntity.badRequest()
                    .body(AdminApprovalResponse.builder()
                            .success(false)
                            .message(e.getMessage())
                            .build());
        }
    }

    @PutMapping("/reject/mentor/{userId}")
    @Operation(summary = "Reject Mentor Application", description = "Reject a mentor application with optional reason")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Mentor rejected successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request or rejection failed"),
            @ApiResponse(responseCode = "404", description = "Mentor application not found")
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminApprovalResponse> rejectMentor(
            @Parameter(description = "User ID of the mentor to reject") @PathVariable Long userId,
            @Valid @RequestBody AdminApprovalRequest request) {
        try {
            log.info("Admin rejecting mentor application for user ID: {} with reason: {}", userId,
                    request.getRejectionReason());
            AdminApprovalResponse response = adminApprovalService.rejectMentor(userId,
                    request.getRejectionReason());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error rejecting mentor for user ID: {}", userId, e);
            return ResponseEntity.badRequest()
                    .body(AdminApprovalResponse.builder()
                            .success(false)
                            .message(e.getMessage())
                            .build());
        }
    }

    @PutMapping("/reject/recruiter/{userId}")
    @Operation(summary = "Reject Recruiter Application", description = "Reject a recruiter application with optional reason")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Recruiter rejected successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request or rejection failed"),
            @ApiResponse(responseCode = "404", description = "Recruiter application not found")
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminApprovalResponse> rejectRecruiter(
            @Parameter(description = "User ID of the recruiter to reject") @PathVariable Long userId,
            @Valid @RequestBody AdminApprovalRequest request) {
        try {
            log.info("Admin rejecting recruiter application for user ID: {} with reason: {}", userId,
                    request.getRejectionReason());
            AdminApprovalResponse response = adminApprovalService.rejectRecruiter(userId,
                    request.getRejectionReason());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error rejecting recruiter for user ID: {}", userId, e);
            return ResponseEntity.badRequest()
                    .body(AdminApprovalResponse.builder()
                            .success(false)
                            .message(e.getMessage())
                            .build());
        }
    }
}