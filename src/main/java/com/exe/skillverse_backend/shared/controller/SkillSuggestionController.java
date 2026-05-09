package com.exe.skillverse_backend.shared.controller;

import com.exe.skillverse_backend.shared.dto.SkillSuggestionDto;
import com.exe.skillverse_backend.shared.service.SkillSuggestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/skill-suggestions")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "Skill Suggestion", description = "Public API for suggesting new skills")
public class SkillSuggestionController {

    private final SkillSuggestionService skillSuggestionService;

    @PostMapping
    @Operation(summary = "Suggest a new skill")
    public ResponseEntity<SkillSuggestionDto> suggestSkill(
            @Valid @RequestBody SkillSuggestionDto dto,
            @org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.oauth2.jwt.Jwt jwt) {
        log.info("User suggesting skill: {}", dto.getSuggestedName());
        Long userId = jwt != null ? com.exe.skillverse_backend.shared.util.JwtUtils.extractUserId(jwt) : 1L; 
        SkillSuggestionDto created = skillSuggestionService.createSuggestion(dto, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
