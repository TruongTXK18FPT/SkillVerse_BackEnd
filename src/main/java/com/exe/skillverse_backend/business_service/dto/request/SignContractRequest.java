package com.exe.skillverse_backend.business_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SignContractRequest {
    @NotBlank
    private String action; // "SIGN" or "REJECT"

    private String signatureImageUrl;

    private String rejectionReason;

    // Set by controller (not from request body)
    private String ipAddress;
    private String userAgent;
}