package com.exe.skillverse_backend.report_service.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for creating a new violation report
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateViolationReportRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must be less than 255 characters")
    private String title;

    // Either reportedUserId or reportedUserEmail must be provided
    private Long reportedUserId;
    
    // Email of the user being reported (alternative to ID)
    private String reportedUserEmail;

    @NotNull(message = "Report type is required")
    private String reportType; // INAPPROPRIATE_CONTENT, HARASSMENT, SPAM, FRAUD, etc.

    @Builder.Default
    private String severity = "MEDIUM"; // LOW, MEDIUM, HIGH

    @NotBlank(message = "Description is required")
    @Size(min = 20, message = "Description must be at least 20 characters for a proper report")
    private String description;

    /**
     * List of evidence items to attach to the report
     */
    @Valid
    private List<EvidenceRequest> evidences;

    /**
     * Nested DTO for evidence items
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EvidenceRequest {

        @NotNull(message = "Evidence type is required")
        private String evidenceType; // SCREENSHOT, DOCUMENT, VIDEO, AUDIO, LINK, CHAT_LOG, OTHER

        private String fileUrl;

        private String fileName;

        private String description;

        private String externalLink;

        private Long fileSize;

        private String mimeType;
    }
}
