package com.exe.skillverse_backend.identity_verification_service.controller;

import org.springframework.security.oauth2.jwt.Jwt;
import com.exe.skillverse_backend.identity_verification_service.service.IdentityVerificationService;
import com.exe.skillverse_backend.mentor_service.entity.MentorProfile;
import com.exe.skillverse_backend.mentor_service.repository.MentorProfileRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/identity")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Identity Verification", description = "Endpoints for CCCD identity verification")
public class IdentityVerificationController {

    private final IdentityVerificationService identityVerificationService;
    private final MentorProfileRepository mentorProfileRepository;

    /**
     * [Admin] Get list of mentors who have submitted CCCD and are waiting for identity verification.
     * These are APPROVED mentors whose cccdExtractedData is populated but identityVerified = false.
     */
    @GetMapping("/admin/pending-cccd")
    @Operation(summary = "[Admin] List mentors awaiting CCCD verification", description = "Returns mentors with extracted CCCD data that have not been identity-verified yet.")
    public ResponseEntity<List<Map<String, Object>>> getPendingCccdVerifications() {
        try {
            List<MentorProfile> pending = mentorProfileRepository.findPendingCccdVerifications();
            List<Map<String, Object>> result = pending.stream().map(m -> {
                Map<String, Object> dto = new HashMap<>();
                dto.put("userId", m.getUserId());
                dto.put("fullName", m.getFullName());
                dto.put("email", m.getEmail());
                dto.put("cccdNumber", m.getCccdNumber());
                dto.put("cccdFullName", m.getCccdFullName());
                dto.put("cccdDob", m.getCccdDob());
                dto.put("cccdExtractedData", m.getCccdExtractedData());
                dto.put("identityVerified", m.getIdentityVerified());
                dto.put("applicationStatus", m.getApplicationStatus());
                dto.put("updatedAt", m.getUpdatedAt());
                return dto;
            }).toList();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to fetch pending CCCD verifications", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping(value = "/upload-cccd", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload CCCD for existing mentors", description = "Extracts CCCD info via FPT.AI and updates the mentor profile.")
    public ResponseEntity<Map<String, Object>> uploadMentorCccd(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam("cccdFrontFile") MultipartFile cccdFrontFile,
            @RequestParam("cccdBackFile") MultipartFile cccdBackFile) {
        
        try {
            identityVerificationService.verifyLegacyMentor(Long.parseLong(jwt.getSubject()), cccdFrontFile, cccdBackFile);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Hồ sơ CCCD đã được gửi. Đang chờ Admin kiểm duyệt.");
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid identity verification request: {}", e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            log.error("Failed to process identity verification", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Internal server error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @PostMapping("/admin/approve-cccd/{userId}")
    @Operation(summary = "[Admin] Approve CCCD identity verification", description = "Admin approves a mentor's supplemental CCCD submission. Sets identityVerified=true and sends email.")
    public ResponseEntity<Map<String, Object>> adminApproveMentorCccd(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long userId) {
        
        try {
            Long adminId = Long.parseLong(jwt.getSubject());
            identityVerificationService.adminApproveCccd(userId, adminId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "CCCD identity verification approved. Email sent to mentor.");
            response.put("userId", userId);

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            log.error("Failed to admin approve CCCD for userId: {}", userId, e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Internal server error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}
