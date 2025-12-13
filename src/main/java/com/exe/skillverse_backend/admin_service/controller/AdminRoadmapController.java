package com.exe.skillverse_backend.admin_service.controller;

import com.exe.skillverse_backend.ai_service.dto.response.RoadmapSessionSummary;
import com.exe.skillverse_backend.ai_service.service.AiRoadmapService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/roadmaps")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Roadmap Management", description = "Admin endpoints for managing roadmaps")
@SecurityRequirement(name = "Bearer Authentication")
public class AdminRoadmapController {

    private final AiRoadmapService aiRoadmapService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get All Roadmaps", description = "Retrieve all roadmap sessions (Admin only)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved all roadmaps"),
            @ApiResponse(responseCode = "403", description = "Access denied")
    })
    public ResponseEntity<List<RoadmapSessionSummary>> getAllRoadmaps() {
        log.info("Fetching all roadmaps for admin");
        List<RoadmapSessionSummary> roadmaps = aiRoadmapService.getAllRoadmaps();
        return ResponseEntity.ok(roadmaps);
    }
}
