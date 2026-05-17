package com.exe.skillverse_backend.roadmap_package_service.controller;

import com.exe.skillverse_backend.roadmap_package_service.dto.request.RoadmapTemplateRequest;
import com.exe.skillverse_backend.roadmap_package_service.dto.response.RoadmapTemplateResponse;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/roadmap-templates")
@RequiredArgsConstructor
public class RoadmapTemplateController {

    private final RoadmapTemplateService roadmapTemplateService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('CONTENT_ADMIN') or hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<RoadmapTemplateResponse> createTemplate(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody RoadmapTemplateRequest request) {
        Long actorId = JwtUtils.extractUserId(jwt);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(roadmapTemplateService.createTemplate(actorId, request));
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('ADMIN') or hasRole('CONTENT_ADMIN') or hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<List<RoadmapTemplateResponse>> myTemplates(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        Long actorId = JwtUtils.extractUserId(jwt);
        return ResponseEntity.ok(roadmapTemplateService.getMyTemplates(actorId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<RoadmapTemplateResponse> getTemplate(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id) {
        Long actorId = JwtUtils.extractUserId(jwt);
        return ResponseEntity.ok(roadmapTemplateService.getTemplate(actorId, id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('CONTENT_ADMIN') or hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<RoadmapTemplateResponse> updateTemplate(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @Valid @RequestBody RoadmapTemplateRequest request) {
        Long actorId = JwtUtils.extractUserId(jwt);
        return ResponseEntity.ok(roadmapTemplateService.updateTemplate(actorId, id, request));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasRole('ADMIN') or hasRole('CONTENT_ADMIN') or hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<RoadmapTemplateResponse> submitTemplate(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id) {
        Long actorId = JwtUtils.extractUserId(jwt);
        return ResponseEntity.ok(roadmapTemplateService.submitTemplate(actorId, id));
    }
}
