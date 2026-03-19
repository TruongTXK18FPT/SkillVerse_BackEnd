package com.exe.skillverse_backend.report_service.dto.response;

import com.exe.skillverse_backend.report_service.entity.ReportEvidence;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for report evidence details
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvidenceResponse {

    private Long id;
    private String evidenceType;
    private String fileUrl;
    private String fileName;
    private String description;
    private String externalLink;
    private Long fileSize;
    private String mimeType;
    private LocalDateTime createdAt;

    /**
     * Convert entity to response DTO
     */
    public static EvidenceResponse fromEntity(ReportEvidence evidence) {
        return EvidenceResponse.builder()
                .id(evidence.getId())
                .evidenceType(evidence.getEvidenceType() != null ? evidence.getEvidenceType().name() : null)
                .fileUrl(evidence.getFileUrl())
                .fileName(evidence.getFileName())
                .description(evidence.getDescription())
                .externalLink(evidence.getExternalLink())
                .fileSize(evidence.getFileSize())
                .mimeType(evidence.getMimeType())
                .createdAt(evidence.getCreatedAt())
                .build();
    }
}
