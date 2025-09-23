package com.exe.skillverse_backend.portfolio_service.dto.response;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserCertificateResponseDto {

    private Long id;
    private Long userId;
    private Long certificateId;
    private String certificateName; // For convenience
    private String certificateIssuer; // For convenience
    private LocalDate issueDate;
    private LocalDate expiresAt;
    private Long fileId;
    private String fileUrl; // For convenience
    private Long verifiedBy;
    private String verifiedByName; // For convenience
    private LocalDateTime createdAt;
    private boolean isExpired; // Computed field for convenience
}