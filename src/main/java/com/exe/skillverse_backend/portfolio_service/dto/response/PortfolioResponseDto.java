package com.exe.skillverse_backend.portfolio_service.dto.response;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortfolioResponseDto {

    private Long userId;
    private String userFullName;
    private String userEmail;
    private LocalDateTime generatedAt;

    // Summary statistics only - no nested objects
    private int totalProjects;
    private int totalProducts;
    private int totalCertificates;
    private int activeCertificates; // Non-expired certificates
    private int completedProjects;
    private int inProgressProjects;
    private int availableProducts;
}