package com.exe.skillverse_backend.ai_service.controller;

import com.exe.skillverse_backend.ai_service.dto.request.ExpertPromptRequest;
import com.exe.skillverse_backend.ai_service.entity.ExpertPromptConfig;
import com.exe.skillverse_backend.ai_service.repository.ExpertPromptConfigRepository;
import com.exe.skillverse_backend.ai_service.service.ExpertPromptMediaService;
import com.exe.skillverse_backend.ai_service.service.ExpertPromptServiceImpl;
import com.exe.skillverse_backend.shared.exception.ApiException;
import com.exe.skillverse_backend.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/expert-prompts")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin - AI Expert Prompts", description = "Manage expert personas and prompts")
@PreAuthorize("hasRole('ADMIN') or hasRole('AI_ADMIN')")
public class ExpertPromptAdminController {

    private final ExpertPromptConfigRepository expertPromptConfigRepository;
    private final ExpertPromptMediaService expertPromptMediaService;
    private final ExpertPromptServiceImpl expertPromptService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('AI_ADMIN')")
    @Operation(summary = "Create new Expert Prompt", description = "Add a new industry/role and its expert system prompt")
    public ResponseEntity<ExpertPromptConfig> createExpertPrompt(@Valid @RequestBody ExpertPromptRequest request) {
        if (expertPromptConfigRepository.findByDomainAndIndustryAndJobRoleAndIsActiveTrue(
                request.getDomain(), request.getIndustry(), request.getJobRole()).isPresent()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Prompt config already exists for this role");
        }

        String systemPrompt = request.getSystemPrompt();
        if ((systemPrompt == null || systemPrompt.isBlank())
                && (request.getDomainRules() != null || request.getRolePrompt() != null)) {
            systemPrompt = buildSystemPrompt(request.getDomainRules(), request.getRolePrompt(), request.getJobRole());
        }

        ExpertPromptConfig config = ExpertPromptConfig.builder()
                .domain(request.getDomain())
                .industry(request.getIndustry())
                .jobRole(request.getJobRole())
                .keywords(request.getKeywords())
                .domainRules(request.getDomainRules())
                .rolePrompt(request.getRolePrompt())
                .systemPrompt(systemPrompt != null ? systemPrompt : "")
                .mediaUrl(request.getMediaUrl())
                .isActive(request.isActive())
                .build();

        return ResponseEntity.ok(expertPromptConfigRepository.save(config));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('AI_ADMIN')")
    @Operation(summary = "Update Expert Prompt", description = "Update an existing prompt configuration")
    public ResponseEntity<ExpertPromptConfig> updateExpertPrompt(
            @PathVariable Long id,
            @Valid @RequestBody ExpertPromptRequest request) {

        ExpertPromptConfig config = expertPromptConfigRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Prompt config not found"));

        String systemPrompt = request.getSystemPrompt();
        if ((systemPrompt == null || systemPrompt.isBlank())
                && (request.getDomainRules() != null || request.getRolePrompt() != null)) {
            systemPrompt = buildSystemPrompt(request.getDomainRules(), request.getRolePrompt(), request.getJobRole());
        }

        config.setDomain(request.getDomain());
        config.setIndustry(request.getIndustry());
        config.setJobRole(request.getJobRole());
        config.setKeywords(request.getKeywords());
        config.setDomainRules(request.getDomainRules());
        config.setRolePrompt(request.getRolePrompt());
        config.setSystemPrompt(systemPrompt != null ? systemPrompt : config.getSystemPrompt());
        config.setMediaUrl(request.getMediaUrl());
        config.setActive(request.isActive());

        return ResponseEntity.ok(expertPromptConfigRepository.save(config));
    }

    private String buildSystemPrompt(String domainRules, String rolePrompt, String jobRole) {
        StringBuilder sb = new StringBuilder();
        sb.append("# MEOWL AI - CHUYÊN GIA ").append(jobRole.toUpperCase()).append("\n\n");

        if (domainRules != null && !domainRules.isBlank()) {
            sb.append("## QUY TẮC LĨNH VỰC\n");
            sb.append(domainRules).append("\n\n");
        }

        if (rolePrompt != null && !rolePrompt.isBlank()) {
            sb.append("## CHUYÊN MÔN VAI TRÒ\n");
            sb.append(rolePrompt).append("\n\n");
        }

        return sb.toString();
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('AI_ADMIN')")
    @Operation(summary = "List all Expert Prompts", description = "Get all prompt configurations")
    public ResponseEntity<List<ExpertPromptConfig>> getAllPrompts() {
        return ResponseEntity.ok(expertPromptConfigRepository.findAll());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('AI_ADMIN')")
    @Operation(summary = "Get Expert Prompt by ID")
    public ResponseEntity<ExpertPromptConfig> getPromptById(@PathVariable Long id) {
        return ResponseEntity.ok(expertPromptConfigRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Prompt config not found")));
    }

    @GetMapping("/match")
    @PreAuthorize("hasRole('ADMIN') or hasRole('AI_ADMIN')")
    @Operation(summary = "Find matching Expert Prompt", description = "Find expert prompt config by domain, industry and job role")
    public ResponseEntity<ExpertPromptConfig> findMatchingPrompt(
            @RequestParam String domain,
            @RequestParam String industry,
            @RequestParam String jobRole) {

        return expertPromptConfigRepository.findByDomainAndIndustryAndJobRoleAndIsActiveTrue(domain, industry, jobRole)
                .map(ResponseEntity::ok)
                .orElseGet(() -> {
                    String domainPattern = (domain == null || domain.isBlank()) ? null : "%" + domain + "%";
                    String industryPattern = (industry == null || industry.isBlank()) ? null : "%" + industry + "%";
                    String rolePattern = (jobRole == null || jobRole.isBlank())
                            ? null
                            : "%" + jobRole.trim().toLowerCase() + "%";

                    if (rolePattern != null) {
                        List<ExpertPromptConfig> fuzzyMatches = expertPromptConfigRepository.findMatchingPrompts(
                                domainPattern,
                                industryPattern,
                                rolePattern);

                        if (!fuzzyMatches.isEmpty()) {
                            return ResponseEntity.ok(fuzzyMatches.get(0));
                        }
                    }

                    String resolvedSystemPrompt = expertPromptService.getSystemPrompt(domain, industry, jobRole);
                    if (resolvedSystemPrompt == null || resolvedSystemPrompt.isBlank()) {
                        throw new ApiException(ErrorCode.NOT_FOUND,
                                "Không tìm thấy cấu hình prompt phù hợp cho ngành và vị trí đã chọn");
                    }

                    ExpertPromptConfig fallbackConfig = ExpertPromptConfig.builder()
                            .id(0L)
                            .domain(domain)
                            .industry(industry)
                            .jobRole(jobRole)
                            .keywords("Đang áp dụng prompt dự phòng từ ExpertPromptService vì chưa có bản ghi cấu hình lưu trực tiếp.")
                            .systemPrompt(resolvedSystemPrompt)
                            .isActive(true)
                            .build();

                    return ResponseEntity.ok(fallbackConfig);
                });
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('AI_ADMIN')")
    @Operation(summary = "Delete Expert Prompt")
    public ResponseEntity<Void> deletePrompt(@PathVariable Long id) {
        if (!expertPromptConfigRepository.existsById(id)) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Prompt config not found");
        }
        expertPromptConfigRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/{id}/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN') or hasRole('AI_ADMIN')")
    @Operation(summary = "Upload media for Expert Prompt", description = "Upload an icon/image for a specific expert role to Cloudinary")
    public ResponseEntity<Map<String, String>> uploadMedia(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {

        String mediaUrl = expertPromptMediaService.uploadMedia(id, file);
        return ResponseEntity.ok(Map.of(
                "message", "Media uploaded successfully",
                "mediaUrl", mediaUrl));
    }

    @DeleteMapping("/{id}/media")
    @PreAuthorize("hasRole('ADMIN') or hasRole('AI_ADMIN')")
    @Operation(summary = "Delete media for Expert Prompt", description = "Remove the media URL from expert prompt config")
    public ResponseEntity<Map<String, String>> deleteMedia(@PathVariable Long id) {
        expertPromptMediaService.deleteMedia(id);
        return ResponseEntity.ok(Map.of("message", "Media deleted successfully"));
    }

    @PutMapping("/{id}/media-url")
    @PreAuthorize("hasRole('ADMIN') or hasRole('AI_ADMIN')")
    @Operation(summary = "Update media URL directly", description = "Set media URL directly (for admin to paste Cloudinary URL)")
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
                "mediaUrl", updatedUrl));
    }
}
