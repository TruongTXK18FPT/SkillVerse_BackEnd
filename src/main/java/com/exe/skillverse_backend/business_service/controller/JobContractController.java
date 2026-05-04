package com.exe.skillverse_backend.business_service.controller;

import com.exe.skillverse_backend.business_service.dto.request.CreateContractRequest;
import com.exe.skillverse_backend.business_service.dto.request.OnboardingInfoRequest;
import com.exe.skillverse_backend.business_service.dto.request.SignContractRequest;
import com.exe.skillverse_backend.business_service.dto.request.UpdateContractRequest;
import com.exe.skillverse_backend.business_service.dto.response.JobContractResponse;
import com.exe.skillverse_backend.business_service.dto.response.OnboardingInfoResponse;
import com.exe.skillverse_backend.business_service.service.JobContractService;
import com.exe.skillverse_backend.identity_verification_service.dto.IdCardExtractionResult;
import com.exe.skillverse_backend.shared.util.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/contracts")
@RequiredArgsConstructor
@Slf4j
@Validated
public class JobContractController {

    private final JobContractService contractService;

    @PostMapping
    @PreAuthorize("hasRole('RECRUITER') or hasRole('ADMIN')")
    public ResponseEntity<JobContractResponse> createContract(
            @Valid @RequestBody CreateContractRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        Long userId = JwtUtils.extractUserId(jwt);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(contractService.createContract(request, userId));
    }

    @GetMapping("/my")
    public ResponseEntity<List<JobContractResponse>> getMyContracts(
            @RequestParam String role,
            @AuthenticationPrincipal Jwt jwt) {
        Long userId = JwtUtils.extractUserId(jwt);
        return ResponseEntity.ok(contractService.getMyContracts(role, userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobContractResponse> getContract(@PathVariable Long id) {
        return ResponseEntity.ok(contractService.getContractById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('RECRUITER') or hasRole('ADMIN')")
    public ResponseEntity<JobContractResponse> updateContract(
            @PathVariable Long id,
            @Valid @RequestBody UpdateContractRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        Long userId = JwtUtils.extractUserId(jwt);
        return ResponseEntity.ok(contractService.updateContract(id, request, userId));
    }

    @PatchMapping("/{id}/send")
    @PreAuthorize("hasRole('RECRUITER') or hasRole('ADMIN')")
    public ResponseEntity<JobContractResponse> sendForSignature(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        Long userId = JwtUtils.extractUserId(jwt);
        return ResponseEntity.ok(contractService.sendForSignature(id, userId));
    }

    @PostMapping("/{id}/sign")
    public ResponseEntity<JobContractResponse> signContract(
            @PathVariable Long id,
            @Valid @RequestBody SignContractRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest httpRequest) {
        Long userId = JwtUtils.extractUserId(jwt);
        request.setIpAddress(getClientIp(httpRequest));
        request.setUserAgent(httpRequest.getHeader("User-Agent"));
        return ResponseEntity.ok(contractService.signContract(id, request, userId));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<JobContractResponse> rejectContract(
            @PathVariable Long id,
            @RequestParam(required = false) String reason,
            @AuthenticationPrincipal Jwt jwt) {
        Long userId = JwtUtils.extractUserId(jwt);
        return ResponseEntity.ok(contractService.rejectContract(id, reason, userId));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<JobContractResponse> cancelContract(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        Long userId = JwtUtils.extractUserId(jwt);
        return ResponseEntity.ok(contractService.cancelContract(id, userId));
    }

    @GetMapping("/applications/{appId}")
    public ResponseEntity<JobContractResponse> getByApplication(@PathVariable Long appId) {
        return ResponseEntity.ok(contractService.getContractByApplication(appId));
    }

    // ==================== ONBOARDING & OCR ENDPOINTS ====================

    /**
     * OCR: Extract CCCD info from an uploaded image via FPT AI.
     * The image is NOT stored — only text data is returned.
     */
    @PostMapping(value = "/applications/{appId}/ocr-id-card", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<IdCardExtractionResult> ocrIdCard(
            @PathVariable Long appId,
            @RequestParam("image") MultipartFile image,
            @AuthenticationPrincipal Jwt jwt) {
        Long userId = JwtUtils.extractUserId(jwt);
        return ResponseEntity.ok(contractService.extractIdCardForApplication(appId, image, userId));
    }

    /**
     * Submit onboarding info (CCCD text + Bank account).
     * Transitions application to AWAITING_ONBOARDING_INFO → ready for contract.
     */
    @PostMapping("/applications/{appId}/onboarding")
    public ResponseEntity<OnboardingInfoResponse> submitOnboardingInfo(
            @PathVariable Long appId,
            @Valid @RequestBody OnboardingInfoRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        Long userId = JwtUtils.extractUserId(jwt);
        return ResponseEntity.ok(contractService.submitOnboardingInfo(appId, request, userId));
    }

    /**
     * Get onboarding info that was previously submitted for an application.
     */
    @GetMapping("/applications/{appId}/onboarding")
    public ResponseEntity<OnboardingInfoResponse> getOnboardingInfo(
            @PathVariable Long appId,
            @AuthenticationPrincipal Jwt jwt) {
        Long userId = JwtUtils.extractUserId(jwt);
        return ResponseEntity.ok(contractService.getOnboardingInfo(appId, userId));
    }

    /**
     * Send a reminder email and notification to candidate to submit their onboarding info.
     */
    @PostMapping("/applications/{appId}/onboarding/remind")
    @PreAuthorize("hasRole('RECRUITER') or hasRole('ADMIN')")
    public ResponseEntity<Void> remindOnboardingInfo(
            @PathVariable Long appId,
            @AuthenticationPrincipal Jwt jwt) {
        Long userId = JwtUtils.extractUserId(jwt);
        contractService.remindOnboardingInfo(appId, userId);
        return ResponseEntity.ok().build();
    }

    /**
     * Get the most recently submitted onboarding info for the candidate to reuse.
     */
    @GetMapping("/onboarding-info/me")
    public ResponseEntity<OnboardingInfoResponse> getLatestOnboardingInfo(
            @AuthenticationPrincipal Jwt jwt) {
        Long userId = JwtUtils.extractUserId(jwt);
        OnboardingInfoResponse info = contractService.getLatestOnboardingInfo(userId);
        if (info == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(info);
    }

    /**
     * Upload a custom contract PDF (Cloudinary) for an application.
     * Recruiter uploads their company's contract template.
     */
    @PostMapping(value = "/{id}/upload-pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('RECRUITER') or hasRole('ADMIN')")
    public ResponseEntity<JobContractResponse> uploadContractPdf(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "startDate", required = false) java.time.LocalDate startDate,
            @RequestParam("endDate") java.time.LocalDate endDate,
            @AuthenticationPrincipal Jwt jwt) {
        Long userId = JwtUtils.extractUserId(jwt);
        return ResponseEntity.ok(contractService.uploadContractPdf(id, file, startDate, endDate, userId));
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) return request.getRemoteAddr();
        return xfHeader.split(",")[0];
    }
}

