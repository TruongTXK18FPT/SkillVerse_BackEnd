package com.exe.skillverse_backend.portfolio_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortfolioVerifiedSkillEvidenceDTO {
    private Long id;
    private String type;
    private String title;
    private String url;
    private String imageUrl;
    private String issuer;
    private String description;
}
