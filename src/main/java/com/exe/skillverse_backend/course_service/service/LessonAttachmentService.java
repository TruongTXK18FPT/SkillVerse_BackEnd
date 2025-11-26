package com.exe.skillverse_backend.course_service.service;

import com.exe.skillverse_backend.course_service.dto.attachmentdto.AddAttachmentRequest;
import com.exe.skillverse_backend.course_service.dto.attachmentdto.LessonAttachmentDTO;

import java.util.List;

/**
 * Service interface for managing lesson attachments (PDFs, links, etc.)
 */
public interface LessonAttachmentService {

    /**
     * Add attachment to a lesson
     */
    LessonAttachmentDTO addAttachment(Long lessonId, AddAttachmentRequest request, Long actorId);

    /**
     * List all attachments for a lesson
     */
    List<LessonAttachmentDTO> listAttachments(Long lessonId);

    /**
     * Delete an attachment
     */
    void deleteAttachment(Long attachmentId, Long actorId);

    /**
     * Update attachment order
     */
    void reorderAttachments(Long lessonId, List<Long> attachmentIds, Long actorId);
}
