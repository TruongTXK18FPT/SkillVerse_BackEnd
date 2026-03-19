package com.exe.skillverse_backend.portfolio_service.dto;

import com.exe.skillverse_backend.portfolio_service.entity.ExternalCertificate;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExternalCertificateDTO {
    private Long id;
    private Long userId;
    private String title;
    private String issuingOrganization;
    private LocalDate issueDate;
    private LocalDate expiryDate;
    private String credentialId;
    private String credentialUrl;
    private String description;
    private String certificateImageUrl;
    private List<String> skills;
    private ExternalCertificate.CertificateCategory category;
    private Boolean isVerified;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
