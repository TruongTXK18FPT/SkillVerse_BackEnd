package com.exe.skillverse_backend.business_service.controller;

import com.exe.skillverse_backend.business_service.entity.EscrowTransaction;
import com.exe.skillverse_backend.business_service.entity.JobEscrow;
import com.exe.skillverse_backend.business_service.service.EscrowService;
import com.exe.skillverse_backend.shared.util.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/short-term-jobs")
@RequiredArgsConstructor
@Slf4j
public class EscrowController {

    private final EscrowService escrowService;

    @PostMapping("/{jobId}/fund-escrow")
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<JobEscrow> fundEscrow(@PathVariable Long jobId, Authentication auth) {
        Long userId = JwtUtils.extractUserId(auth);
        log.info("POST /api/short-term-jobs/{}/fund-escrow by user {}", jobId, userId);
        JobEscrow escrow = escrowService.fundEscrow(jobId, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(escrow);
    }

    @GetMapping("/{jobId}/escrow")
    public ResponseEntity<JobEscrow> getEscrow(@PathVariable Long jobId) {
        log.info("GET /api/short-term-jobs/{}/escrow", jobId);
        JobEscrow escrow = escrowService.getEscrowByJobId(jobId);
        if (escrow == null) {
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.ok(escrow);
    }

    @PostMapping("/{jobId}/release-escrow")
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<JobEscrow> releaseEscrow(
            @PathVariable Long jobId,
            @RequestParam(required = false) String message,
            Authentication auth) {
        Long userId = JwtUtils.extractUserId(auth);
        log.info("POST /api/short-term-jobs/{}/release-escrow by user {}", jobId, userId);
        return ResponseEntity.ok(escrowService.releaseEscrow(jobId, userId, message));
    }

    @PostMapping("/{jobId}/refund-escrow")
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<JobEscrow> refundEscrow(
            @PathVariable Long jobId,
            @RequestParam(required = false) String reason,
            @RequestBody(required = false) Map<String, String> payload,
            Authentication auth) {
        Long userId = JwtUtils.extractUserId(auth);
        String resolvedReason = reason != null && !reason.isBlank()
                ? reason
                : payload != null ? payload.get("reason") : null;
        log.info("POST /api/short-term-jobs/{}/refund-escrow by user {}", jobId, userId);
        return ResponseEntity.ok(escrowService.refundEscrow(jobId, userId, resolvedReason));
    }

    @GetMapping("/{jobId}/escrow-transactions")
    public ResponseEntity<Page<EscrowTransaction>> getTransactions(
            @PathVariable Long jobId,
            @PageableDefault(size = 20) Pageable pageable) {
        log.info("GET /api/short-term-jobs/{}/escrow-transactions", jobId);
        return ResponseEntity.ok(escrowService.getEscrowTransactionsPaged(jobId, pageable));
    }
}
