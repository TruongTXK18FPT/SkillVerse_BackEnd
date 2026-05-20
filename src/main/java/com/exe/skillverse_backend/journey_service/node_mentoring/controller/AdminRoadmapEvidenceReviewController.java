package com.exe.skillverse_backend.journey_service.node_mentoring.controller;

import com.exe.skillverse_backend.journey_service.node_mentoring.dto.request.AdminReviewDecisionRequest;
import com.exe.skillverse_backend.journey_service.node_mentoring.dto.response.AdminRoadmapEvidenceReviewResponse;
import com.exe.skillverse_backend.journey_service.node_mentoring.entity.AiReviewStatus;
import com.exe.skillverse_backend.journey_service.node_mentoring.entity.RoadmapEvidenceAiReview;
import com.exe.skillverse_backend.journey_service.node_mentoring.repository.RoadmapEvidenceAiReviewRepository;
import com.exe.skillverse_backend.journey_service.node_mentoring.ai.service.RoadmapEvidenceAiReviewService;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import com.exe.skillverse_backend.shared.util.JwtUtils;
import org.springframework.security.oauth2.jwt.Jwt;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/roadmap-evidence-reviews")
@Tag(name = "Admin Roadmap Evidence Review API")
@RequiredArgsConstructor
@Slf4j
public class AdminRoadmapEvidenceReviewController {

    private final RoadmapEvidenceAiReviewRepository reviewRepository;
    private final RoadmapEvidenceAiReviewService aiReviewService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTENT_ADMIN', 'AI_ADMIN')")
    @Operation(summary = "Get list of evidence reviews")
    public ResponseEntity<Page<AdminRoadmapEvidenceReviewResponse>> getReviews(
            @RequestParam(required = false) AiReviewStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        
        Page<RoadmapEvidenceAiReview> reviews;
        if (status != null) {
            reviews = reviewRepository.findByStatus(status, pageable);
        } else {
            reviews = reviewRepository.findAll(pageable);
        }
        
        return ResponseEntity.ok(reviews.map(AdminRoadmapEvidenceReviewResponse::from));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTENT_ADMIN', 'AI_ADMIN')")
    @Operation(summary = "Get evidence review details")
    public ResponseEntity<AdminRoadmapEvidenceReviewResponse> getReviewDetail(@PathVariable Long id) {
        RoadmapEvidenceAiReview review = reviewRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Review not found"));
        return ResponseEntity.ok(AdminRoadmapEvidenceReviewResponse.from(review));
    }

    @PostMapping("/{id}/decide")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTENT_ADMIN', 'AI_ADMIN')")
    @Operation(summary = "Approve or reject a review")
    public ResponseEntity<AdminRoadmapEvidenceReviewResponse> decideReview(
            @PathVariable Long id,
            @Valid @RequestBody AdminReviewDecisionRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Long adminId = JwtUtils.extractUserId(jwt);
        RoadmapEvidenceAiReview review = aiReviewService.decideReview(id, request.getDecision(), request.getReason(), adminId);
        return ResponseEntity.ok(AdminRoadmapEvidenceReviewResponse.from(review));
    }
}
