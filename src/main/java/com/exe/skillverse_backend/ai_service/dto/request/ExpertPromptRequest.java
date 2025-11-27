package com.exe.skillverse_backend.ai_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpertPromptRequest {
    
    @NotBlank(message = "Domain is required")
    private String domain;
    
    @NotBlank(message = "Industry is required")
    private String industry;
    
    @NotBlank(message = "Job role is required")
    private String jobRole;
    
    private String keywords;
    
    /**
     * Domain-specific rules (optional)
     * e.g., "Trong lĩnh vực IT, luôn cập nhật công nghệ mới nhất..."
     */
    private String domainRules;
    
    /**
     * Role-specific prompt (optional)  
     * e.g., "Backend Developer cần nắm vững API design, database..."
     */
    private String rolePrompt;
    
    /**
     * Full system prompt (auto-built if domainRules + rolePrompt provided)
     */
    private String systemPrompt;
    
    /**
     * Media URL (icon/image) for this role
     */
    private String mediaUrl;
    
    @Builder.Default
    private boolean isActive = true;
}
