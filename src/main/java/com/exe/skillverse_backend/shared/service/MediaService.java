package com.exe.skillverse_backend.shared.service;

import com.exe.skillverse_backend.shared.dto.MediaDTO;
import com.exe.skillverse_backend.shared.dto.PageResponse;
import org.springframework.data.domain.Pageable;

import java.io.InputStream;
import java.util.List;

/**
 * Service for managing media files (images, videos, documents, etc.)
 * Handles upload, attachment to courses/lessons, and file operations
 */
public interface MediaService {

    /**
     * Upload a new media file
     * 
     * @param actorId ID of the user uploading the file
     * @param fileName original filename
     * @param contentType MIME type of the file
     * @param fileSize size of the file in bytes
     * @param data input stream of file data
     * @return MediaDTO containing metadata of uploaded file
     */
    MediaDTO upload(Long actorId, String fileName, String contentType, long fileSize, InputStream data);

    /**
     * Attach media to a course (e.g., as thumbnail)
     * 
     * @param mediaId ID of the media file
     * @param courseId ID of the course
     * @param actorId ID of the user performing the action
     * @return updated MediaDTO
     */
    MediaDTO attachToCourse(Long mediaId, Long courseId, Long actorId);

    /**
     * Attach media to a lesson (e.g., as video content)
     */
    MediaDTO attachToLesson(Long mediaId, Long lessonId, Long actorId);

    /**
     * Detach media from course/lesson (remove association but keep file)
     */
    void detach(Long mediaId, Long actorId);

    /**
     * Delete media file (soft delete if supported, otherwise hard delete)
     */
    void delete(Long mediaId, Long actorId);

    /**
     * Get media file metadata by ID
     */
    MediaDTO get(Long mediaId);

    /**
     * List media files owned by a specific user
     */
    PageResponse<MediaDTO> listByOwner(Long ownerUserId, Pageable pageable);

    /**
     * List media files associated with a course
     */
    List<MediaDTO> listByCourse(Long courseId);

    /**
     * List media files associated with a lesson
     */
    List<MediaDTO> listByLesson(Long lessonId);

    /**
     * Get signed URL for accessing media file
     * For S3/CloudFront: generates temporary signed URL
     * For local storage: returns regular URL
     */
    String getSignedUrl(Long mediaId, Long actorId);

    /**
     * Search media files by filename
     */
    PageResponse<MediaDTO> searchByFileName(String query, Pageable pageable);

    /**
     * Validate if file type and size are allowed
     */
    void validateFile(String contentType, long fileSize);
}