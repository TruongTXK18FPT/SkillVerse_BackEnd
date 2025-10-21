package com.exe.skillverse_backend.portfolio_service.dto;

import com.exe.skillverse_backend.portfolio_service.entity.ExternalCertificate;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

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
