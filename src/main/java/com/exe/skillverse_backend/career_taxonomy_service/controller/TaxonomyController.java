package com.exe.skillverse_backend.career_taxonomy_service.controller;

import com.exe.skillverse_backend.career_taxonomy_service.dto.DomainDto;
import com.exe.skillverse_backend.career_taxonomy_service.dto.JobPositionDto;
import com.exe.skillverse_backend.career_taxonomy_service.dto.JobPositionTrackDto;
import com.exe.skillverse_backend.career_taxonomy_service.dto.JobPositionTrackSkillDto;
import com.exe.skillverse_backend.career_taxonomy_service.service.TaxonomyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Career Taxonomy", description = "Public Read-Only API for Career Taxonomy")
public class TaxonomyController {

    private final TaxonomyService taxonomyService;

    @GetMapping("/domains")
    @Operation(summary = "List all active domains")
    public ResponseEntity<List<DomainDto>> listDomains() {
        return ResponseEntity.ok(taxonomyService.listActiveDomains());
    }

    @GetMapping("/job-positions")
    @Operation(summary = "List all active job positions, optionally filtered by domain")
    public ResponseEntity<List<JobPositionDto>> listJobPositions(@RequestParam(required = false) Long domainId) {
        return ResponseEntity.ok(taxonomyService.listActiveJobPositions(domainId));
    }

    @GetMapping("/job-positions/{id}/tracks")
    @Operation(summary = "List all active tracks for a job position")
    public ResponseEntity<List<JobPositionTrackDto>> listTracks(@PathVariable Long id) {
        return ResponseEntity.ok(taxonomyService.listActiveTracks(id));
    }

    @GetMapping("/job-position-tracks/{trackId}/skills")
    @Operation(summary = "List all skills mapped to a track")
    public ResponseEntity<List<JobPositionTrackSkillDto>> listTrackSkills(@PathVariable Long trackId) {
        return ResponseEntity.ok(taxonomyService.listTrackSkills(trackId));
    }
}
