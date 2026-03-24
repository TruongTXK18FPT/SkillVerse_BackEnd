package com.exe.skillverse_backend.business_service.dto.request;

import com.exe.skillverse_backend.business_service.entity.DisputeEvidence.EvidenceType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitEvidenceRequest {
    @NotNull
    private EvidenceType evidenceType;

    private String content;

    private String fileUrl;

    private String fileName;

    private String description;
}
