package com.exe.skillverse_backend.ai_service.controller;

import com.exe.skillverse_backend.ai_service.dto.request.ExpertPromptRequest;
import com.exe.skillverse_backend.ai_service.entity.ExpertPromptConfig;
import com.exe.skillverse_backend.ai_service.repository.ExpertPromptConfigRepository;
import com.exe.skillverse_backend.ai_service.service.ExpertPromptMediaService;
import com.exe.skillverse_backend.shared.exception.ApiException;
import com.exe.skillverse_backend.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/expert-prompts")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin - AI Expert Prompts", description = "Manage expert personas and prompts")
public class ExpertPromptAdminController {

    private final ExpertPromptConfigRepository expertPromptConfigRepository;
    private final ExpertPromptMediaService expertPromptMediaService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create new Expert Prompt", description = "Add a new industry/role and its expert system prompt")
    public ResponseEntity<ExpertPromptConfig> createExpertPrompt(@Valid @RequestBody ExpertPromptRequest request) {
        // Check duplicate
        if (expertPromptConfigRepository.findByDomainAndIndustryAndJobRoleAndIsActiveTrue(
                request.getDomain(), request.getIndustry(), request.getJobRole()).isPresent()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Prompt config already exists for this role");
        }

        ExpertPromptConfig config = ExpertPromptConfig.builder()
                .domain(request.getDomain())
                .industry(request.getIndustry())
                .jobRole(request.getJobRole())
                .keywords(request.getKeywords())
                .systemPrompt(request.getSystemPrompt())
                .mediaUrl(request.getMediaUrl())
                .isActive(request.isActive())
                .build();

        return ResponseEntity.ok(expertPromptConfigRepository.save(config));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update Expert Prompt", description = "Update an existing prompt configuration")
    public ResponseEntity<ExpertPromptConfig> updateExpertPrompt(
            @PathVariable Long id,
            @Valid @RequestBody ExpertPromptRequest request) {
        
        ExpertPromptConfig config = expertPromptConfigRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Prompt config not found"));

        config.setDomain(request.getDomain());
        config.setIndustry(request.getIndustry());
        config.setJobRole(request.getJobRole());
        config.setKeywords(request.getKeywords());
        config.setSystemPrompt(request.getSystemPrompt());
        config.setMediaUrl(request.getMediaUrl());
        config.setActive(request.isActive());

        return ResponseEntity.ok(expertPromptConfigRepository.save(config));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List all Expert Prompts", description = "Get all prompt configurations")
    public ResponseEntity<List<ExpertPromptConfig>> getAllPrompts() {
        return ResponseEntity.ok(expertPromptConfigRepository.findAll());
    }
    
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get Expert Prompt by ID")
    public ResponseEntity<ExpertPromptConfig> getPromptById(@PathVariable Long id) {
        return ResponseEntity.ok(expertPromptConfigRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Prompt config not found")));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete Expert Prompt")
    public ResponseEntity<Void> deletePrompt(@PathVariable Long id) {
        if (!expertPromptConfigRepository.existsById(id)) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Prompt config not found");
        }
        expertPromptConfigRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== MEDIA MANAGEMENT ====================

    @PostMapping(value = "/{id}/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Upload media for Expert Prompt", 
               description = "Upload an icon/image for a specific expert role to Cloudinary")
    public ResponseEntity<Map<String, String>> uploadMedia(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        
        String mediaUrl = expertPromptMediaService.uploadMedia(id, file);
        return ResponseEntity.ok(Map.of(
            "message", "Media uploaded successfully",
            "mediaUrl", mediaUrl
        ));
    }

    @DeleteMapping("/{id}/media")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete media for Expert Prompt", 
               description = "Remove the media URL from expert prompt config")
    public ResponseEntity<Map<String, String>> deleteMedia(@PathVariable Long id) {
        expertPromptMediaService.deleteMedia(id);
        return ResponseEntity.ok(Map.of("message", "Media deleted successfully"));
    }

    @PutMapping("/{id}/media-url")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update media URL directly", 
               description = "Set media URL directly (for admin to paste Cloudinary URL)")
    public ResponseEntity<Map<String, String>> updateMediaUrl(
            @PathVariable Long id,
            @RequestBody Map<String, String> request) {
        
        String mediaUrl = request.get("mediaUrl");
        if (mediaUrl == null || mediaUrl.trim().isEmpty()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "mediaUrl is required");
        }
        
        String updatedUrl = expertPromptMediaService.updateMediaUrl(id, mediaUrl);
        return ResponseEntity.ok(Map.of(
            "message", "Media URL updated successfully",
            "mediaUrl", updatedUrl
        ));
    }
}
