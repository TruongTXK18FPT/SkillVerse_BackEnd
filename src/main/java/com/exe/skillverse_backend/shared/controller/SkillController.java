package com.exe.skillverse_backend.shared.controller;

import com.exe.skillverse_backend.shared.dto.SkillDto;
import com.exe.skillverse_backend.shared.dto.PageResponse;
import com.exe.skillverse_backend.shared.service.SkillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@RestController
@RequestMapping("/api/skills")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "Skill Management", description = "APIs for managing skills and skill hierarchy")
public class SkillController {

    private final SkillService skillService;

    @PostMapping
    @Operation(summary = "Create a new skill")
    public ResponseEntity<SkillDto> createSkill(@Valid @RequestBody SkillDto dto) {
        log.info("Creating skill: {}", dto.getName());
        SkillDto created = skillService.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing skill")
    public ResponseEntity<SkillDto> updateSkill(
            @Parameter(description = "Skill ID") @PathVariable @NotNull Long id,
            @Valid @RequestBody SkillDto dto) {
        log.info("Updating skill id: {}", id);
        SkillDto updated = skillService.update(id, dto);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a skill")
    public ResponseEntity<Void> deleteSkill(
            @Parameter(description = "Skill ID") @PathVariable @NotNull Long id) {
        log.info("Deleting skill id: {}", id);
        skillService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get skill by ID")
    public ResponseEntity<SkillDto> getSkill(
            @Parameter(description = "Skill ID") @PathVariable @NotNull Long id) {
        SkillDto skill = skillService.get(id);
        return ResponseEntity.ok(skill);
    }

    @GetMapping("/search")
    @Operation(summary = "Search skills by name or description")
    public ResponseEntity<PageResponse<SkillDto>> searchSkills(
            @Parameter(description = "Search query") @RequestParam(required = false) String q,
            @PageableDefault(size = 20) Pageable pageable) {
        PageResponse<SkillDto> results = skillService.search(q, pageable);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/categories/{category}")
    @Operation(summary = "List skills by category")
    public ResponseEntity<PageResponse<SkillDto>> getSkillsByCategory(
            @Parameter(description = "Skill category") @PathVariable String category,
            @PageableDefault(size = 20) Pageable pageable) {
        PageResponse<SkillDto> results = skillService.listByCategory(category, pageable);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/roots")
    @Operation(summary = "List root skills (skills without parent)")
    public ResponseEntity<PageResponse<SkillDto>> getRootSkills(
            @PageableDefault(size = 20) Pageable pageable) {
        PageResponse<SkillDto> results = skillService.listRoots(pageable);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/{id}/children")
    @Operation(summary = "Get direct children of a skill")
    public ResponseEntity<List<SkillDto>> getSkillChildren(
            @Parameter(description = "Parent skill ID") @PathVariable @NotNull Long id) {
        List<SkillDto> children = skillService.listChildren(id);
        return ResponseEntity.ok(children);
    }

    @PostMapping("/{id}/reparent")
    @Operation(summary = "Change parent of a skill")
    public ResponseEntity<SkillDto> reparentSkill(
            @Parameter(description = "Skill ID") @PathVariable @NotNull Long id,
            @Parameter(description = "New parent skill ID (null for root)") @RequestParam(required = false) Long newParentId) {
        log.info("Reparenting skill id: {} to parentId: {}", id, newParentId);
        SkillDto updated = skillService.reparent(id, newParentId);
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/{id}/path")
    @Operation(summary = "Get path from skill to root")
    public ResponseEntity<List<Long>> getPathToRoot(
            @Parameter(description = "Skill ID") @PathVariable @NotNull Long id) {
        List<Long> path = skillService.pathToRoot(id);
        return ResponseEntity.ok(path);
    }

    @GetMapping("/suggest")
    @Operation(summary = "Get skill suggestions by name prefix")
    public ResponseEntity<PageResponse<SkillDto>> suggestSkills(
            @Parameter(description = "Name prefix") @RequestParam String prefix,
            @PageableDefault(size = 10) Pageable pageable) {
        PageResponse<SkillDto> suggestions = skillService.suggestByPrefix(prefix, pageable);
        return ResponseEntity.ok(suggestions);
    }
}