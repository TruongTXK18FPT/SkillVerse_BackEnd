package com.exe.skillverse_backend.question_bank_service.controller;

import com.exe.skillverse_backend.question_bank_service.dto.request.SkillResolveRequest;
import com.exe.skillverse_backend.question_bank_service.dto.response.SkillResolveResponse;
import com.exe.skillverse_backend.question_bank_service.service.SkillResolveService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for AI-powered skill resolution.
 * Uses the same AI model as quiz generation to analyze a skill name
 * and determine which domain/industry/jobRole it belongs to.
 */
@RestController
@RequestMapping("/api/v1/question-banks/skills")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Skill Resolution", description = "AI-powered skill to career path resolution")
public class SkillResolveController {

    private final SkillResolveService skillResolveService;

    @PostMapping("/resolve")
    @PreAuthorize("hasRole('ADMIN') or hasRole('AI_ADMIN') or hasRole('MENTOR')")
    @Operation(summary = "Resolve skill to domain/industry/jobRole using AI",
            description = "Analyzes the given skill name and returns the best matching career path. " +
                    "Also checks if a question bank already exists for that path.")
    public ResponseEntity<SkillResolveResponse> resolveSkill(
            @Valid @RequestBody SkillResolveRequest request) {
        log.info("Resolving skill: {}", request.getSkillName());
        return ResponseEntity.ok(skillResolveService.resolveSkill(request.getSkillName()));
    }

    @PostMapping("/resolve-and-create")
    @PreAuthorize("hasRole('ADMIN') or hasRole('AI_ADMIN')")
    @Operation(summary = "Resolve skill and auto-create question bank",
            description = "Analyzes the skill, determines the career path, and automatically " +
                    "creates a question bank if one doesn't exist for that path + skill.")
    public ResponseEntity<SkillResolveResponse> resolveAndCreateBank(
            @Valid @RequestBody SkillResolveRequest request) {
        log.info("Resolving and creating QB for skill: {}", request.getSkillName());
        return ResponseEntity.ok(skillResolveService.resolveAndCreateQuestionBank(request.getSkillName()));
    }
}
