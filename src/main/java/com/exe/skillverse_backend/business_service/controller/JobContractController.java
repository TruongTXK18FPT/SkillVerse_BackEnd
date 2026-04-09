package com.exe.skillverse_backend.business_service.controller;

import com.exe.skillverse_backend.business_service.dto.request.CreateContractRequest;
import com.exe.skillverse_backend.business_service.dto.request.SignContractRequest;
import com.exe.skillverse_backend.business_service.dto.request.UpdateContractRequest;
import com.exe.skillverse_backend.business_service.dto.response.JobContractResponse;
import com.exe.skillverse_backend.business_service.service.JobContractService;
import com.exe.skillverse_backend.shared.util.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) return request.getRemoteAddr();
        return xfHeader.split(",")[0];
    }
}
