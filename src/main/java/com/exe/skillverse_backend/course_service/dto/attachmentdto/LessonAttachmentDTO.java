package com.exe.skillverse_backend.course_service.dto.attachmentdto;

import com.exe.skillverse_backend.course_service.entity.enums.AttachmentType;
import lombok.*;

/**
 * DTO for LessonAttachment entity
 * Used to transfer attachment data to frontend
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LessonAttachmentDTO {
    private Long id;
    private String title;
    private String description;
    private String downloadUrl;
    private AttachmentType type;
    private Long fileSize;
    private String fileSizeFormatted;
    private Integer orderIndex;
    private String createdAt;
}
