package com.exe.skillverse_backend.career_taxonomy_service.controller;

import com.exe.skillverse_backend.career_taxonomy_service.dto.DomainDto;
import com.exe.skillverse_backend.career_taxonomy_service.dto.JobPositionDto;
import com.exe.skillverse_backend.career_taxonomy_service.dto.JobPositionTrackDto;
import com.exe.skillverse_backend.career_taxonomy_service.dto.JobPositionTrackSkillDto;
import com.exe.skillverse_backend.career_taxonomy_service.service.TaxonomyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Career Taxonomy", description = "Admin Write API for Career Taxonomy")
public class TaxonomyAdminController {

    private final TaxonomyService taxonomyService;

    // --- Domain ---
    @GetMapping("/domains")
    @Operation(summary = "List all domains (including inactive)")
    public ResponseEntity<List<DomainDto>> listDomains() {
        return ResponseEntity.ok(taxonomyService.listAllDomains());
    }

    @PostMapping("/domains")
    @Operation(summary = "Create a domain")
    public ResponseEntity<DomainDto> createDomain(@Valid @RequestBody DomainDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(taxonomyService.createDomain(dto));
    }

    @PutMapping("/domains/{id}")
    @Operation(summary = "Update a domain")
    public ResponseEntity<DomainDto> updateDomain(@PathVariable Long id, @Valid @RequestBody DomainDto dto) {
        return ResponseEntity.ok(taxonomyService.updateDomain(id, dto));
    }

    @DeleteMapping("/domains/{id}")
    @Operation(summary = "Deactivate a domain")
    public ResponseEntity<Void> deactivateDomain(@PathVariable Long id) {
        taxonomyService.deactivateDomain(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/domains/{id}/reactivate")
    @Operation(summary = "Reactivate a domain")
    public ResponseEntity<Void> reactivateDomain(@PathVariable Long id) {
        taxonomyService.reactivateDomain(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/domains/{id}/hard")
    @Operation(summary = "Hard delete a domain")
    public ResponseEntity<Void> hardDeleteDomain(@PathVariable Long id) {
        taxonomyService.hardDeleteDomain(id);
        return ResponseEntity.noContent().build();
    }

    // --- Job Position ---
    @GetMapping("/job-positions")
    @Operation(summary = "List all job positions (including inactive)")
    public ResponseEntity<List<JobPositionDto>> listJobPositions(
            @RequestParam(required = false) Long domainId) {
        return ResponseEntity.ok(taxonomyService.listAllJobPositions(domainId));
    }

    @PostMapping("/job-positions")
    @Operation(summary = "Create a job position")
    public ResponseEntity<JobPositionDto> createJobPosition(@Valid @RequestBody JobPositionDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(taxonomyService.createJobPosition(dto));
    }

    @PutMapping("/job-positions/{id}")
    @Operation(summary = "Update a job position")
    public ResponseEntity<JobPositionDto> updateJobPosition(@PathVariable Long id, @Valid @RequestBody JobPositionDto dto) {
        return ResponseEntity.ok(taxonomyService.updateJobPosition(id, dto));
    }

    @DeleteMapping("/job-positions/{id}")
    @Operation(summary = "Deactivate a job position")
    public ResponseEntity<Void> deactivateJobPosition(@PathVariable Long id) {
        taxonomyService.deactivateJobPosition(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/job-positions/{id}/reactivate")
    @Operation(summary = "Reactivate a job position")
    public ResponseEntity<Void> reactivateJobPosition(@PathVariable Long id) {
        taxonomyService.reactivateJobPosition(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/job-positions/{id}/hard")
    @Operation(summary = "Hard delete a job position")
    public ResponseEntity<Void> hardDeleteJobPosition(@PathVariable Long id) {
        taxonomyService.hardDeleteJobPosition(id);
        return ResponseEntity.noContent().build();
    }

    // --- Job Position Track ---
    @GetMapping("/job-position-tracks")
    @Operation(summary = "List all tracks (including inactive)")
    public ResponseEntity<List<JobPositionTrackDto>> listTracks(
            @RequestParam(required = false) Long jobPositionId) {
        return ResponseEntity.ok(taxonomyService.listAllTracks(jobPositionId));
    }

    @PostMapping("/job-position-tracks")
    @Operation(summary = "Create a job position track")
    public ResponseEntity<JobPositionTrackDto> createTrack(@Valid @RequestBody JobPositionTrackDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(taxonomyService.createTrack(dto));
    }

    @PutMapping("/job-position-tracks/{id}")
    @Operation(summary = "Update a job position track")
    public ResponseEntity<JobPositionTrackDto> updateTrack(@PathVariable Long id, @Valid @RequestBody JobPositionTrackDto dto) {
        return ResponseEntity.ok(taxonomyService.updateTrack(id, dto));
    }

    @DeleteMapping("/job-position-tracks/{id}")
    @Operation(summary = "Deactivate a job position track")
    public ResponseEntity<Void> deactivateTrack(@PathVariable Long id) {
        taxonomyService.deactivateTrack(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/job-position-tracks/{id}/reactivate")
    @Operation(summary = "Reactivate a job position track")
    public ResponseEntity<Void> reactivateTrack(@PathVariable Long id) {
        taxonomyService.reactivateTrack(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/job-position-tracks/{id}/hard")
    @Operation(summary = "Hard delete a job position track")
    public ResponseEntity<Void> hardDeleteTrack(@PathVariable Long id) {
        taxonomyService.hardDeleteTrack(id);
        return ResponseEntity.noContent().build();
    }

    // --- Track Skills ---
    @PostMapping("/job-position-tracks/{trackId}/skills")
    @Operation(summary = "Bulk update skills for a track")
    public ResponseEntity<List<JobPositionTrackSkillDto>> updateTrackSkills(
            @PathVariable Long trackId,
            @RequestBody List<JobPositionTrackSkillDto> skillDtos) {
        return ResponseEntity.ok(taxonomyService.updateTrackSkills(trackId, skillDtos));
    }
}
