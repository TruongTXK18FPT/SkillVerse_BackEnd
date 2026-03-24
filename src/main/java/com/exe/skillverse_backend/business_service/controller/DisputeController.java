package com.exe.skillverse_backend.business_service.controller;

import com.exe.skillverse_backend.business_service.dto.request.OpenDisputeRequest;
import com.exe.skillverse_backend.business_service.dto.request.ResolveDisputeRequest;
import com.exe.skillverse_backend.business_service.dto.request.SubmitEvidenceRequest;
import com.exe.skillverse_backend.business_service.entity.Dispute;
import com.exe.skillverse_backend.business_service.entity.DisputeEvidence;
import com.exe.skillverse_backend.business_service.entity.DisputeResponseEntity;
import com.exe.skillverse_backend.business_service.service.DisputeService;
import com.exe.skillverse_backend.shared.exception.BadRequestException;
import com.exe.skillverse_backend.shared.util.JwtUtils;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/disputes")
@RequiredArgsConstructor
@Slf4j
public class DisputeController {

    private final DisputeService disputeService;

    @PostMapping({"", "/open"})
    @PreAuthorize("hasAnyRole('RECRUITER', 'USER')")
    public ResponseEntity<Dispute> openDispute(
            @Valid @RequestBody OpenDisputeRequest request,
            Authentication auth) {
        Long userId = JwtUtils.extractUserId(auth);
        log.info("POST /api/disputes/open - Opening dispute for job {} by user {}", request.getJobId(), userId);
        Dispute dispute = disputeService.openDispute(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(dispute);
    }

    @GetMapping("/{disputeId}")
    public ResponseEntity<Dispute> getDispute(@PathVariable Long disputeId) {
        log.info("GET /api/disputes/{}", disputeId);
        return ResponseEntity.ok(disputeService.getDispute(disputeId));
    }

    @GetMapping("/job/{jobId}")
    public ResponseEntity<Dispute> getDisputesByJob(@PathVariable Long jobId) {
        log.info("GET /api/disputes/job/{}", jobId);
        List<Dispute> disputes = disputeService.getDisputesByJob(jobId);
        if (disputes.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(disputes.get(0));
    }

    @GetMapping("/my-disputes")
    @PreAuthorize("hasAnyRole('RECRUITER', 'USER')")
    public ResponseEntity<Page<Dispute>> getMyDisputes(
            Authentication auth,
            @PageableDefault(size = 20) Pageable pageable) {
        Long userId = JwtUtils.extractUserId(auth);
        log.info("GET /api/disputes/my-disputes for user {}", userId);
        return ResponseEntity.ok(disputeService.getMyDisputes(userId, pageable));
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<Dispute>> getAllDisputes(
            @PageableDefault(size = 20) Pageable pageable) {
        log.info("GET /api/disputes/all");
        return ResponseEntity.ok(disputeService.getAllDisputes(pageable));
    }

    @PostMapping("/{disputeId}/evidence")
    @PreAuthorize("hasAnyRole('RECRUITER', 'USER')")
    public ResponseEntity<DisputeEvidence> submitEvidence(
            @PathVariable Long disputeId,
            @Valid @RequestBody SubmitEvidenceRequest request,
            Authentication auth) {
        Long userId = JwtUtils.extractUserId(auth);
        log.info("POST /api/disputes/{}/evidence by user {}", disputeId, userId);
        DisputeEvidence evidence = disputeService.submitEvidence(userId, disputeId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(evidence);
    }

    @GetMapping("/{disputeId}/evidence")
    public ResponseEntity<List<DisputeEvidence>> getDisputeEvidence(@PathVariable Long disputeId) {
        log.info("GET /api/disputes/{}/evidence", disputeId);
        return ResponseEntity.ok(disputeService.getDisputeEvidence(disputeId));
    }

    @PostMapping("/{disputeId}/evidence/{evidenceId}/respond")
    @PreAuthorize("hasAnyRole('RECRUITER', 'USER')")
    public ResponseEntity<DisputeResponseEntity> respondToEvidence(
            @PathVariable Long disputeId,
            @PathVariable Long evidenceId,
            @RequestParam(required = false) String content,
            @RequestBody(required = false) Map<String, String> payload,
            Authentication auth) {
        Long userId = JwtUtils.extractUserId(auth);
        String resolvedContent = content != null && !content.isBlank()
                ? content
                : payload != null ? payload.get("content") : null;
        if (resolvedContent == null || resolvedContent.isBlank()) {
            throw new BadRequestException("Nội dung phản hồi không được để trống.");
        }
        log.info("POST /api/disputes/{}/evidence/{}/respond by user {}", disputeId, evidenceId, userId);
        DisputeResponseEntity response = disputeService.respondToEvidence(userId, disputeId, evidenceId, resolvedContent);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{disputeId}/resolve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Dispute> resolveDispute(
            @PathVariable Long disputeId,
            @Valid @RequestBody ResolveDisputeRequest request,
            Authentication auth) {
        Long adminId = JwtUtils.extractUserId(auth);
        log.info("POST /api/disputes/{}/resolve by admin {}", disputeId, adminId);
        Dispute dispute = disputeService.resolveDispute(adminId, disputeId, request);
        return ResponseEntity.ok(dispute);
    }
}
