package com.exe.skillverse_backend.portfolio_service.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CVGenerationRequest {
    private String templateName; // PROFESSIONAL, CREATIVE, MINIMAL, MODERN
    private String targetRole; // Optional: target job role
    private String targetIndustry; // Optional: target industry
    private String additionalInstructions; // Optional: any special requirements
    private Boolean includeProjects;
    private Boolean includeCertificates;
    private Boolean includeReviews;
}
