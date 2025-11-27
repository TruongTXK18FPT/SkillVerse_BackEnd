package com.exe.skillverse_backend.course_service.dto.attachmentdto;

import com.exe.skillverse_backend.course_service.entity.enums.AttachmentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Request DTO for adding attachment to lesson
 * Supports both uploaded files (via mediaId) and external links (via
 * externalUrl)
 */
@Data
public class AddAttachmentRequest {

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    // For uploaded files (PDF, DOCX, etc.)
    private Long mediaId;

    // For external links (Google Drive, GitHub, etc.)
    private String externalUrl;

    @NotNull(message = "Attachment type is required")
    private AttachmentType type;

    private Integer orderIndex;
}
