package com.exe.skillverse_backend.ai_service.controller;

import com.exe.skillverse_backend.ai_service.dto.response.ExpertFieldResponse;
import com.exe.skillverse_backend.ai_service.entity.ExpertPromptConfig;
import com.exe.skillverse_backend.ai_service.repository.ExpertPromptConfigRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controller for retrieving available expert fields, industries, and roles
 * Used by frontend to populate selection UI for EXPERT_MODE
 */
@RestController
@RequestMapping("/api/v1/expert-fields")
@RequiredArgsConstructor
@Tag(name = "Expert Fields", description = "APIs for retrieving available expert domains and roles")
public class ExpertFieldController {

    private final ExpertPromptConfigRepository expertPromptConfigRepository;

    /**
     * Get all available expert fields organized by domain -> industry -> roles
     * 
     * @return Hierarchical structure of available expert fields
     */
    @GetMapping
    @Operation(summary = "Get all expert fields", 
               description = "Returns all available domains, industries, and job roles for expert mode selection")
    public ResponseEntity<List<ExpertFieldResponse>> getAllExpertFields() {
        List<ExpertPromptConfig> allConfigs = expertPromptConfigRepository.findAll();
        
        // Group by domain -> industry -> roles
        Map<String, Map<String, List<ExpertPromptConfig>>> groupedData = allConfigs.stream()
            .collect(Collectors.groupingBy(
                config -> config.getDomain() != null ? config.getDomain() : "General",
                LinkedHashMap::new,
                Collectors.groupingBy(
                    config -> config.getIndustry() != null ? config.getIndustry() : "General",
                    LinkedHashMap::new,
                    Collectors.toList()
                )
            ));
        
        // Build response structure
        List<ExpertFieldResponse> response = new ArrayList<>();
        
        for (Map.Entry<String, Map<String, List<ExpertPromptConfig>>> domainEntry : groupedData.entrySet()) {
            String domain = domainEntry.getKey();
            List<ExpertFieldResponse.IndustryInfo> industries = new ArrayList<>();
            
            for (Map.Entry<String, List<ExpertPromptConfig>> industryEntry : domainEntry.getValue().entrySet()) {
                String industry = industryEntry.getKey();
                List<ExpertFieldResponse.RoleInfo> roles = industryEntry.getValue().stream()
                    .map(config -> ExpertFieldResponse.RoleInfo.builder()
                        .jobRole(config.getJobRole())
                        .keywords(config.getKeywords())
                        .mediaUrl(config.getMediaUrl())
                        .isActive(config.isActive())
                        .build())
                    .collect(Collectors.toList());
                
                industries.add(ExpertFieldResponse.IndustryInfo.builder()
                    .industry(industry)
                    .roles(roles)
                    .build());
            }
            
            response.add(ExpertFieldResponse.builder()
                .domain(domain)
                .industries(industries)
                .build());
        }
        
        return ResponseEntity.ok(response);
    }

    /**
     * Get simplified list of all job roles (flat structure)
     * Useful for autocomplete/search functionality
     * 
     * @return List of all available job roles
     */
    @GetMapping("/roles")
    @Operation(summary = "Get all job roles", 
               description = "Returns a flat list of all available job roles")
    public ResponseEntity<List<String>> getAllJobRoles() {
        List<String> roles = expertPromptConfigRepository.findAll().stream()
            .filter(ExpertPromptConfig::isActive)
            .map(ExpertPromptConfig::getJobRole)
            .distinct()
            .sorted()
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(roles);
    }

    /**
     * Get all unique domains
     * 
     * @return List of all available domains
     */
    @GetMapping("/domains")
    @Operation(summary = "Get all domains", 
               description = "Returns a list of all available domains")
    public ResponseEntity<List<String>> getAllDomains() {
        List<String> domains = expertPromptConfigRepository.findAll().stream()
            .map(config -> config.getDomain() != null ? config.getDomain() : "General")
            .distinct()
            .sorted()
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(domains);
    }

    /**
     * Get all unique industries
     * 
     * @return List of all available industries
     */
    @GetMapping("/industries")
    @Operation(summary = "Get all industries", 
               description = "Returns a list of all available industries")
    public ResponseEntity<List<String>> getAllIndustries() {
        List<String> industries = expertPromptConfigRepository.findAll().stream()
            .map(config -> config.getIndustry() != null ? config.getIndustry() : "General")
            .distinct()
            .sorted()
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(industries);
    }
}
