package com.exe.skillverse_backend.shared.controller;

import com.exe.skillverse_backend.shared.dto.PageResponse;
import com.exe.skillverse_backend.shared.dto.SkillSuggestionDto;
import com.exe.skillverse_backend.shared.service.SkillSuggestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/skill-suggestions")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "Admin Skill Suggestion", description = "Admin API for reviewing skill suggestions")
public class SkillSuggestionAdminController {

    private final SkillSuggestionService skillSuggestionService;

    @GetMapping
    @Operation(summary = "Get pending skill suggestions")
    public ResponseEntity<PageResponse<SkillSuggestionDto>> getPendingSuggestions(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(skillSuggestionService.listPending(pageable));
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve a skill suggestion (creates or merges)")
    public ResponseEntity<SkillSuggestionDto> approveSuggestion(
            @Parameter(description = "Suggestion ID") @PathVariable @NotNull Long id,
            @org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.oauth2.jwt.Jwt jwt) {
        Long adminId = com.exe.skillverse_backend.shared.util.JwtUtils.extractUserId(jwt);
        return ResponseEntity.ok(skillSuggestionService.approve(id, adminId));
    }

    @PostMapping("/{id}/merge")
    @Operation(summary = "Merge a skill suggestion to an existing active skill")
    public ResponseEntity<SkillSuggestionDto> mergeSuggestion(
            @Parameter(description = "Suggestion ID") @PathVariable @NotNull Long id,
            @Parameter(description = "Existing target skill ID") @RequestParam @NotNull Long matchedSkillId,
            @org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.oauth2.jwt.Jwt jwt) {
        Long adminId = com.exe.skillverse_backend.shared.util.JwtUtils.extractUserId(jwt);
        return ResponseEntity.ok(skillSuggestionService.merge(id, matchedSkillId, adminId));
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject a skill suggestion")
    public ResponseEntity<SkillSuggestionDto> rejectSuggestion(
            @Parameter(description = "Suggestion ID") @PathVariable @NotNull Long id,
            @Parameter(description = "Review note") @RequestParam(required = false) String reviewNote,
            @org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.oauth2.jwt.Jwt jwt) {
        Long adminId = com.exe.skillverse_backend.shared.util.JwtUtils.extractUserId(jwt);
        return ResponseEntity.ok(skillSuggestionService.reject(id, reviewNote, adminId));
    }
}
