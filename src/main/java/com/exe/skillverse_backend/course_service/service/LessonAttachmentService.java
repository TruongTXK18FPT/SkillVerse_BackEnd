package com.exe.skillverse_backend.course_service.service;

import com.exe.skillverse_backend.course_service.dto.attachmentdto.AddAttachmentRequest;
import com.exe.skillverse_backend.course_service.dto.attachmentdto.LessonAttachmentDTO;
import java.io.IOException;
import java.util.List;
import org.springframework.http.ResponseEntity;

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
    List<LessonAttachmentDTO> listAttachments(Long lessonId, Long actorId);

    /**
     * Delete an attachment
     */
    void deleteAttachment(Long attachmentId, Long actorId);

    /**
     * Update attachment order
     */
    void reorderAttachments(Long lessonId, List<Long> attachmentIds, Long actorId);

    /**
     * Get download URL for an attachment.
     * Returns a signed URL for Cloudinary-hosted files, direct URL for external links.
     */
    String getDownloadUrlForAttachment(Long attachmentId);

    /**
     * Stream an attachment as a downloadable file with proper Content-Disposition header.
     * Fetches file bytes from Cloudinary and returns with the original filename.
     */
    ResponseEntity<byte[]> streamAttachment(Long attachmentId) throws IOException;

    /**
     * Stream a file given its download URL (used for snapshot attachments with synthetic IDs).
     */
    ResponseEntity<byte[]> streamAttachmentByUrl(String downloadUrl, String filename) throws IOException;
}
