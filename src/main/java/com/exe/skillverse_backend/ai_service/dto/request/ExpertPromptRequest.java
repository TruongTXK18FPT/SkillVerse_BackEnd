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
    
    @NotBlank(message = "System prompt is required")
    private String systemPrompt;
    
    /**
     * Optional media URL (icon/image) for this role
     */
    private String mediaUrl;
    
    @Builder.Default
    private boolean isActive = true;
}
