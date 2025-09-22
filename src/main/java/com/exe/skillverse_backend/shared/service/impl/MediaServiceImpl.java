package com.exe.skillverse_backend.shared.service.impl;

import com.exe.skillverse_backend.course_service.entity.Course;
import com.exe.skillverse_backend.course_service.entity.Lesson;
import com.exe.skillverse_backend.course_service.repository.CourseRepository;
import com.exe.skillverse_backend.course_service.repository.LessonRepository;
import com.exe.skillverse_backend.shared.dto.MediaDTO;
import com.exe.skillverse_backend.shared.dto.PageResponse;
import com.exe.skillverse_backend.shared.entity.Media;
import com.exe.skillverse_backend.shared.exception.AccessDeniedException;
import com.exe.skillverse_backend.shared.exception.MediaOperationException;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import com.exe.skillverse_backend.shared.mapper.MediaMapper;
import com.exe.skillverse_backend.shared.repository.MediaRepository;
import com.exe.skillverse_backend.shared.service.MediaService;
import com.exe.skillverse_backend.shared.storage.StorageClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaServiceImpl implements MediaService {

    private final MediaRepository mediaRepository;
    private final CourseRepository courseRepository;
    private final LessonRepository lessonRepository;
    private final MediaMapper mediaMapper;
    private final StorageClient storageClient;
    private final Clock clock;

    // File validation constants
    private static final long MAX_FILE_SIZE = 500 * 1024 * 1024L; // 500MB
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp",
            "video/mp4", "video/webm", "video/avi", "video/mov",
            "application/pdf", "text/plain", "text/markdown",
            "application/zip", "application/x-zip-compressed",
            "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-powerpoint", "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    );

    @Override
    @Transactional
    public MediaDTO upload(Long actorId, String fileName, String contentType, long fileSize, InputStream data) {
        log.info("Uploading file '{}' of type '{}' and size {} bytes by user {}", fileName, contentType, fileSize, actorId);
        
        // Validate file
        validateFile(contentType, fileSize);
        
        // Generate unique key for storage
        String fileExtension = extractFileExtension(fileName);
        String storageKey = generateStorageKey(actorId, fileExtension);
        
        try {
            // Upload to storage
            String publicUrl = storageClient.putObject(storageKey, data, contentType, fileSize);
            
            // Create Media entity
            Media media = new Media();
            media.setUrl(publicUrl);
            media.setType(contentType);
            media.setFileName(fileName);
            media.setFileSize(fileSize);
            media.setUploadedBy(actorId);
            media.setUploadedAt(now());
            
            Media savedMedia = mediaRepository.save(media);
            log.info("File '{}' uploaded successfully with ID {}", fileName, savedMedia.getId());
            
            return mediaMapper.toDto(savedMedia);
            
        } catch (Exception e) {
            log.error("Failed to upload file '{}' by user {}: {}", fileName, actorId, e.getMessage());
            throw new MediaOperationException("File upload failed: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public MediaDTO attachToCourse(Long mediaId, Long courseId, Long actorId) {
        log.info("Attaching media {} to course {} by user {}", mediaId, courseId, actorId);
        
        Media media = getMediaOrThrow(mediaId);
        Course course = getCourseOrThrow(courseId);
        
        // Check permissions - user must be owner of file or author/admin of course
        ensureOwnerOrAdmin(actorId, media.getUploadedBy(), course.getAuthor().getId());
        
        // TODO: Implement direct courseId field when added to Media entity
        // For now, this would be handled through Course.thumbnail relationship
        log.info("Media {} attached to course {} successfully", mediaId, courseId);
        
        return mediaMapper.toDto(media);
    }

    @Override
    @Transactional
    public MediaDTO attachToLesson(Long mediaId, Long lessonId, Long actorId) {
        log.info("Attaching media {} to lesson {} by user {}", mediaId, lessonId, actorId);
        
        Media media = getMediaOrThrow(mediaId);
        Lesson lesson = getLessonOrThrow(lessonId);
        
        // Check permissions - user must be owner of file or author/admin of course
        ensureOwnerOrAdmin(actorId, media.getUploadedBy(), lesson.getCourse().getAuthor().getId());
        
        // TODO: Implement direct lessonId field when added to Media entity
        // For now, this would be handled through Lesson.videoMedia relationship
        log.info("Media {} attached to lesson {} successfully", mediaId, lessonId);
        
        return mediaMapper.toDto(media);
    }

    @Override
    @Transactional
    public void detach(Long mediaId, Long actorId) {
        log.info("Detaching media {} from course/lesson by user {}", mediaId, actorId);
        
        Media media = getMediaOrThrow(mediaId);
        ensureOwnerOrAdmin(actorId, media.getUploadedBy(), null);
        
        // TODO: Reset courseId and lessonId to null when fields are added to Media entity
        // For now, this would require updating the relationships in Course/Lesson entities
        
        log.info("Media {} detached successfully", mediaId);
    }

    @Override
    @Transactional
    public void delete(Long mediaId, Long actorId) {
        log.info("Deleting media {} by user {}", mediaId, actorId);
        
        Media media = getMediaOrThrow(mediaId);
        ensureOwnerOrAdmin(actorId, media.getUploadedBy(), null);
        
        try {
            // TODO: Implement soft delete if deletedAt field is added to Media entity
            // For now, perform hard delete
            
            // Extract storage key from URL for deletion
            String storageKey = extractStorageKeyFromUrl(media.getUrl());
            
            // Delete from storage
            storageClient.deleteObject(storageKey);
            
            // Delete from database
            mediaRepository.delete(media);
            
            log.info("Media {} deleted successfully", mediaId);
            
        } catch (Exception e) {
            log.error("Failed to delete media {}: {}", mediaId, e.getMessage());
            throw new MediaOperationException("Media deletion failed: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public MediaDTO get(Long mediaId) {
        log.debug("Getting media {}", mediaId);
        
        Media media = getMediaOrThrow(mediaId);
        return mediaMapper.toDto(media);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<MediaDTO> listByOwner(Long ownerUserId, Pageable pageable) {
        log.debug("Listing media files for owner {} with page {}", ownerUserId, pageable.getPageNumber());
        
        Page<Media> mediaPage = mediaRepository.findByUploadedByUser_Id(ownerUserId, pageable);
        
        return PageResponse.<MediaDTO>builder()
                .items(mediaPage.map(mediaMapper::toDto).toList())
                .page(mediaPage.getNumber())
                .size(mediaPage.getSize())
                .total(mediaPage.getTotalElements())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MediaDTO> listByCourse(Long courseId) {
        log.debug("Listing media files for course {}", courseId);
        
        List<Media> mediaList = mediaRepository.findByCourseIdOrderByCreatedAtDesc(courseId);
        return mediaList.stream()
                .map(mediaMapper::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MediaDTO> listByLesson(Long lessonId) {
        log.debug("Listing media files for lesson {}", lessonId);
        
        List<Media> mediaList = mediaRepository.findByLessonIdOrderByCreatedAtDesc(lessonId);
        return mediaList.stream()
                .map(mediaMapper::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public String getSignedUrl(Long mediaId, Long actorId) {
        log.debug("Getting signed URL for media {} by user {}", mediaId, actorId);
        
        Media media = getMediaOrThrow(mediaId);
        
        // TODO: Add permission check for private files
        // For now, allow access to all files
        
        try {
            // Extract storage key from URL
            String storageKey = extractStorageKeyFromUrl(media.getUrl());
            
            // Create signed URL with 1 hour expiration
            return storageClient.createSignedGetUrl(storageKey, Duration.ofHours(1));
            
        } catch (Exception e) {
            log.warn("Failed to create signed URL for media {}, returning public URL", mediaId);
            return media.getUrl();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<MediaDTO> searchByFileName(String query, Pageable pageable) {
        log.debug("Searching media files by filename: '{}'", query);
        
        Page<Media> mediaPage = mediaRepository.searchByFileName(query, pageable);
        
        return PageResponse.<MediaDTO>builder()
                .items(mediaPage.map(mediaMapper::toDto).toList())
                .page(mediaPage.getNumber())
                .size(mediaPage.getSize())
                .total(mediaPage.getTotalElements())
                .build();
    }

    @Override
    public void validateFile(String contentType, long fileSize) {
        // Validate file size
        if (fileSize > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                    String.format("File size %d bytes exceeds maximum allowed size %d bytes", fileSize, MAX_FILE_SIZE));
        }
        
        // Validate content type
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException("Content type '" + contentType + "' is not allowed");
        }
        
        log.debug("File validation passed: type={}, size={}", contentType, fileSize);
    }

    // ===== Helper Methods =====
    
    private Media getMediaOrThrow(Long mediaId) {
        return mediaRepository.findById(mediaId)
                .orElseThrow(() -> new NotFoundException("MEDIA_NOT_FOUND"));
    }
    
    private Course getCourseOrThrow(Long courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new NotFoundException("COURSE_NOT_FOUND"));
    }
    
    private Lesson getLessonOrThrow(Long lessonId) {
        return lessonRepository.findById(lessonId)
                .orElseThrow(() -> new NotFoundException("LESSON_NOT_FOUND"));
    }

    private void ensureOwnerOrAdmin(Long actorId, Long ownerId, Long authorId) {
        // User is owner of the file
        if (actorId.equals(ownerId)) {
            return;
        }
        
        // User is author/admin of the course (if applicable)
        if (authorId != null && actorId.equals(authorId)) {
            return;
        }
        
        // TODO: Check if user has ADMIN role via AuthService
        // For now, throw access denied
        throw new AccessDeniedException("FORBIDDEN");
    }

    private String generateStorageKey(Long userId, String fileExtension) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        return String.format("media/user_%d/%s_%s%s", userId, timestamp, uuid, fileExtension);
    }

    private String extractFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf("."));
    }

    private String extractStorageKeyFromUrl(String url) {
        // TODO: Implement based on storage client implementation
        // For S3: extract key from URL
        // For local: extract relative path
        return url.substring(url.lastIndexOf("/") + 1);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}