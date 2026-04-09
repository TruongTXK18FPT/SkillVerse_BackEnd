package com.exe.skillverse_backend.business_service.dto.response;

import com.exe.skillverse_backend.business_service.enums.SignatureStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ContractSignatureResponse {
    private Long id;
    private Long signedBy;
    private String signedByName;
    private String signedByRole;
    private SignatureStatus status;
    private String signatureImageUrl;
    private LocalDateTime signedAt;
}
