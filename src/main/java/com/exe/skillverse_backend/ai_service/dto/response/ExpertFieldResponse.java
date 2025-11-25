package com.exe.skillverse_backend.ai_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for available expert fields/domains
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpertFieldResponse {
    
    private String domain;
    private List<IndustryInfo> industries;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IndustryInfo {
        private String industry;
        private List<RoleInfo> roles;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoleInfo {
        private String jobRole;
        private String keywords;
        private String mediaUrl;
        private boolean isActive;
    }
}
