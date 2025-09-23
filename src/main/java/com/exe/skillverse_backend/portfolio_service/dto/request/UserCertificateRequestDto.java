package com.exe.skillverse_backend.portfolio_service.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserCertificateRequestDto {

    @NotNull(message = "Certificate ID is required")
    private Long certificateId;

    @NotNull(message = "Issue date is required")
    private LocalDate issueDate;

    private LocalDate expiresAt;

    private Long fileId;

    private Long verifiedBy;
}