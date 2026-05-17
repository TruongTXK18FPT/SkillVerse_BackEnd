package com.exe.skillverse_backend.roadmap_package_service.controller;

import com.exe.skillverse_backend.roadmap_package_service.dto.request.RoadmapTemplateRequest;
import com.exe.skillverse_backend.roadmap_package_service.dto.response.RoadmapTemplateAllocationPreviewResponse;
import com.exe.skillverse_backend.roadmap_package_service.dto.response.RoadmapTemplateCourseCandidateResponse;
import com.exe.skillverse_backend.roadmap_package_service.dto.response.RoadmapTemplateResponse;
import com.exe.skillverse_backend.roadmap_package_service.dto.response.RoadmapTemplateValidationResponse;
import com.exe.skillverse_backend.roadmap_package_service.entity.RoadmapTemplateCourseLinkPolicy;
import com.exe.skillverse_backend.roadmap_package_service.entity.RoadmapTemplateStatus;
import com.exe.skillverse_backend.roadmap_package_service.service.RoadmapTemplateService;
import com.exe.skillverse_backend.shared.util.JwtUtils;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/roadmap-templates")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN') or hasRole('CONTENT_ADMIN') or hasRole('SYSTEM_ADMIN')")
public class AdminRoadmapTemplateController {

    private final RoadmapTemplateService roadmapTemplateService;

    @GetMapping
    public ResponseEntity<List<RoadmapTemplateResponse>> listTemplates(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Long domainId,
            @RequestParam(required = false) Long jobPositionId,
            @RequestParam(required = false) Long jobPositionTrackId,
            @RequestParam(required = false) RoadmapTemplateStatus status) {
        Long adminId = JwtUtils.extractUserId(jwt);
        return ResponseEntity.ok(roadmapTemplateService.listAdminTemplates(
                adminId, domainId, jobPositionId, jobPositionTrackId, status));
    }

    @PostMapping
    public ResponseEntity<RoadmapTemplateResponse> create(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody RoadmapTemplateRequest request) {
        Long adminId = JwtUtils.extractUserId(jwt);
        return ResponseEntity.status(HttpStatus.CREATED).body(roadmapTemplateService.createTemplate(adminId, request));
    }

    @PostMapping("/preview-allocation")
    public ResponseEntity<RoadmapTemplateAllocationPreviewResponse> previewAllocation(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody RoadmapTemplateRequest request) {
        Long adminId = JwtUtils.extractUserId(jwt);
        return ResponseEntity.ok(roadmapTemplateService.previewAllocation(adminId, request));
    }

    @PostMapping("/validate")
    public ResponseEntity<RoadmapTemplateValidationResponse> validateTemplate(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody RoadmapTemplateRequest request) {
        Long adminId = JwtUtils.extractUserId(jwt);
        return ResponseEntity.ok(roadmapTemplateService.validateTemplate(adminId, request));
    }

    @GetMapping("/course-candidates")
    public ResponseEntity<List<RoadmapTemplateCourseCandidateResponse>> courseCandidates(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @RequestParam Long skillId,
            @RequestParam(required = false) RoadmapTemplateCourseLinkPolicy policy,
            @RequestParam(required = false) Integer limit) {
        Long adminId = JwtUtils.extractUserId(jwt);
        return ResponseEntity.ok(roadmapTemplateService.getCourseCandidates(adminId, skillId, policy, limit));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RoadmapTemplateResponse> get(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id) {
        Long adminId = JwtUtils.extractUserId(jwt);
        return ResponseEntity.ok(roadmapTemplateService.getTemplate(adminId, id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RoadmapTemplateResponse> update(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @Valid @RequestBody RoadmapTemplateRequest request) {
        Long adminId = JwtUtils.extractUserId(jwt);
        return ResponseEntity.ok(roadmapTemplateService.updateTemplate(adminId, id, request));
    }

    @GetMapping("/submitted")
    public ResponseEntity<List<RoadmapTemplateResponse>> submittedTemplates() {
        return ResponseEntity.ok(roadmapTemplateService.getSubmittedTemplates());
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<RoadmapTemplateResponse> approve(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id) {
        Long adminId = JwtUtils.extractUserId(jwt);
        return ResponseEntity.ok(roadmapTemplateService.approveTemplate(adminId, id));
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<RoadmapTemplateResponse> publish(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id) {
        Long adminId = JwtUtils.extractUserId(jwt);
        return ResponseEntity.ok(roadmapTemplateService.publishTemplate(adminId, id));
    }

    @PostMapping("/{id}/archive")
    public ResponseEntity<RoadmapTemplateResponse> archive(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id) {
        Long adminId = JwtUtils.extractUserId(jwt);
        return ResponseEntity.ok(roadmapTemplateService.archiveTemplate(adminId, id));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<RoadmapTemplateResponse> reject(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id) {
        Long adminId = JwtUtils.extractUserId(jwt);
        return ResponseEntity.ok(roadmapTemplateService.rejectTemplate(adminId, id));
    }
}
