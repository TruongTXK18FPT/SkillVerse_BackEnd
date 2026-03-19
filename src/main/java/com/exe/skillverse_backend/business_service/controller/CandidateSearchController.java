package com.exe.skillverse_backend.business_service.controller;

import com.exe.skillverse_backend.business_service.dto.request.CandidateSearchRequest;
import com.exe.skillverse_backend.business_service.service.CandidateSearchService;
import com.exe.skillverse_backend.portfolio_service.dto.CandidateSummaryDTO;
import com.exe.skillverse_backend.shared.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for candidate search operations by recruiters
 */
@RestController
@RequestMapping("/api/v1/recruiter/candidates")
@RequiredArgsConstructor
@Slf4j
public class CandidateSearchController {

    private final CandidateSearchService candidateSearchService;

    /**
     * Search candidates with filters
     * GET /api/v1/recruiter/candidates/search
     */
    @GetMapping("/search")
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<?> searchCandidates(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String skills,
            @RequestParam(required = false) Integer minExperience,
            @RequestParam(required = false) Integer maxExperience,
            @RequestParam(required = false) String jobType,
            @RequestParam(required = false) Boolean isRemote,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String experienceLevel,
            @RequestParam(required = false) Integer minHourlyRate,
            @RequestParam(required = false) Integer maxHourlyRate,
            @RequestParam(required = false) Boolean openToOffers,
            @RequestParam(required = false) Boolean hasPortfolio,
            @RequestParam(required = false) Boolean hasCertificates,
            @RequestParam(required = false) Long jobId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "totalScore") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortOrder,
            @RequestParam(defaultValue = "false") Boolean enableAIMatching) {

        Long recruiterId = extractUserId(userDetails);
        log.info("Recruiter {} searching candidates, query: {}, jobId: {}", recruiterId, query, jobId);

        CandidateSearchRequest request = CandidateSearchRequest.builder()
                .query(query)
                .skills(skills)
                .minExperience(minExperience)
                .maxExperience(maxExperience)
                .jobType(jobType)
                .isRemote(isRemote)
                .location(location)
                .experienceLevel(experienceLevel)
                .minHourlyRate(minHourlyRate)
                .maxHourlyRate(maxHourlyRate)
                .openToOffers(openToOffers)
                .hasPortfolio(hasPortfolio)
                .hasCertificates(hasCertificates)
                .jobId(jobId)
                .page(page)
                .size(size)
                .sortBy(sortBy)
                .sortOrder(sortOrder)
                .enableAIMatching(enableAIMatching)
                .build();

        Page<CandidateSummaryDTO> results = candidateSearchService.searchCandidates(recruiterId, request);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Tìm kiếm ứng viên thành công",
                "data", results.getContent(),
                "page", results.getNumber(),
                "size", results.getSize(),
                "totalElements", results.getTotalElements(),
                "totalPages", results.getTotalPages()
        ));
    }

    /**
     * Get AI match explanation for a specific candidate-job pair
     * GET /api/v1/recruiter/candidates/{candidateId}/match
     */
    @GetMapping("/{candidateId}/match")
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<?> getMatchExplanation(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long candidateId,
            @RequestParam Long jobId) {

        Long recruiterId = extractUserId(userDetails);
        log.info("Recruiter {} getting match explanation for candidate {} job {}", recruiterId, candidateId, jobId);

        Object result = candidateSearchService.getCandidateMatchExplanation(recruiterId, jobId, candidateId);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "AI phân tích match",
                "data", result
        ));
    }

    /**
     * Get matching candidates for a specific job
     * GET /api/v1/recruiter/candidates/job/{jobId}
     */
    @GetMapping("/job/{jobId}")
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<?> getMatchingCandidates(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long jobId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Long recruiterId = extractUserId(userDetails);
        log.info("Recruiter {} getting matching candidates for job {}", recruiterId, jobId);

        Page<CandidateSummaryDTO> results = candidateSearchService.getMatchingCandidatesForJob(recruiterId, jobId, page, size);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Danh sách ứng viên phù hợp",
                "data", results.getContent(),
                "page", results.getNumber(),
                "size", results.getSize(),
                "totalElements", results.getTotalElements(),
                "totalPages", results.getTotalPages()
        ));
    }

    /**
     * Shortlist a candidate
     * POST /api/v1/recruiter/candidates/{candidateId}/shortlist
     */
    @PostMapping("/{candidateId}/shortlist")
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<?> shortlistCandidate(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long candidateId,
            @RequestParam(required = false) Long jobId,
            @RequestParam(required = false) String notes) {

        Long recruiterId = extractUserId(userDetails);
        log.info("Recruiter {} shortlisting candidate {} for job {}", recruiterId, candidateId, jobId);

        candidateSearchService.shortlistCandidate(recruiterId, candidateId, jobId, notes);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Đã thêm vào danh sách shortlist"
        ));
    }

    /**
     * Remove candidate from shortlist
     * DELETE /api/v1/recruiter/candidates/{candidateId}/shortlist
     */
    @DeleteMapping("/{candidateId}/shortlist")
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<?> removeFromShortlist(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long candidateId,
            @RequestParam(required = false) Long jobId) {

        Long recruiterId = extractUserId(userDetails);
        log.info("Recruiter {} removing candidate {} from shortlist", recruiterId, candidateId);

        candidateSearchService.removeFromShortlist(recruiterId, candidateId, jobId);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Đã xóa khỏi danh sách shortlist"
        ));
    }

    /**
     * Get shortlisted candidates
     * GET /api/v1/recruiter/candidates/shortlisted
     */
    @GetMapping("/shortlisted")
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<?> getShortlistedCandidates(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Long recruiterId = extractUserId(userDetails);
        log.info("Recruiter {} getting shortlisted candidates", recruiterId);

        Page<CandidateSummaryDTO> results = candidateSearchService.getShortlistedCandidates(recruiterId, status, page, size);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Danh sách ứng viên đã shortlist",
                "data", results.getContent(),
                "page", results.getNumber(),
                "size", results.getSize(),
                "totalElements", results.getTotalElements(),
                "totalPages", results.getTotalPages()
        ));
    }

    /**
     * POST /api/v1/recruiter/candidates/{candidateId}/connect - Connect candidate to a job
     * Creates recruitment session and sends invite
     */
    @PostMapping("/{candidateId}/connect")
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<?> connectCandidateToJob(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long candidateId,
            @RequestParam(required = false) Long jobId) {

        Long recruiterId = extractUserId(userDetails);
        log.info("Recruiter {} connecting candidate {} to job {}", recruiterId, candidateId, jobId);

        var result = candidateSearchService.connectCandidateToJob(recruiterId, candidateId, jobId);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Đã kết nối với ứng viên",
                "data", result
        ));
    }

    /**
     * POST /api/v1/recruiter/candidates/{candidateId}/chat - Start chat with candidate
     * Creates recruitment session if not exists
     */
    @PostMapping("/{candidateId}/chat")
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<?> startChatWithCandidate(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long candidateId,
            @RequestParam(required = false) Long jobId) {

        Long recruiterId = extractUserId(userDetails);
        log.info("Recruiter {} starting chat with candidate {} for job {}", recruiterId, candidateId, jobId);

        var result = candidateSearchService.startChatWithCandidate(recruiterId, candidateId, jobId);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Đã bắt đầu cuộc trò chuyện",
                "data", result
        ));
    }

    private Long extractUserId(UserDetails userDetails) {
        if (userDetails == null) {
            throw new BadRequestException("User not authenticated");
        }
        try {
            return Long.parseLong(userDetails.getUsername());
        } catch (NumberFormatException e) {
            log.error("Failed to parse user ID from: {}", userDetails.getUsername());
            throw new BadRequestException("Invalid user ID format");
        }
    }
}
