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
import com.exe.skillverse_backend.shared.service.CloudinaryService;
import com.exe.skillverse_backend.shared.service.MediaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaServiceImpl implements MediaService {

    private final MediaRepository mediaRepository;
    private final CourseRepository courseRepository;
    private final LessonRepository lessonRepository;
    private final MediaMapper mediaMapper;
    private final CloudinaryService cloudinaryService;
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
        
        try {
            // Convert InputStream to MultipartFile
            MultipartFile multipartFile = convertToMultipartFile(data, fileName, contentType, fileSize);
            
            // Determine file type and upload to Cloudinary
            String folder = "user_" + actorId;
            Map<String, Object> uploadResult;
            
            if (isImage(contentType)) {
                log.debug("Uploading as image to folder: {}", folder);
                uploadResult = cloudinaryService.uploadImage(multipartFile, folder);
            } else if (isVideo(contentType)) {
                log.debug("Uploading as video to folder: {}", folder);
                uploadResult = cloudinaryService.uploadVideo(multipartFile, folder);
            } else {
                log.debug("Uploading as file to folder: {}", folder);
                uploadResult = cloudinaryService.uploadFile(multipartFile, folder);
            }
            
            // Extract Cloudinary response data
            // Cloudinary returns snake_case keys: "url", "public_id", "resource_type"
            String publicUrl = (String) uploadResult.get("url");
            String publicId = (String) uploadResult.get("public_id"); // Fixed: was "publicId" (camelCase)
            String resourceType = (String) uploadResult.get("resource_type"); // Fixed: was "resourceType" (camelCase)
            
            // Create Media entity
            Media media = new Media();
            media.setUrl(publicUrl);
            media.setType(contentType);
            media.setFileName(fileName);
            media.setFileSize(fileSize);
            media.setUploadedBy(actorId);
            media.setUploadedAt(now());
            media.setCloudinaryPublicId(publicId); // Store Cloudinary public ID for deletion
            media.setCloudinaryResourceType(resourceType); // Store resource type
            
            Media savedMedia = mediaRepository.save(media);
            log.info("File '{}' uploaded successfully to Cloudinary with ID {} (public_id: {}, resource_type: {})", 
                    fileName, savedMedia.getId(), publicId, resourceType);
            
            return mediaMapper.toDto(savedMedia);
            
        } catch (IOException e) {
            log.error("Failed to convert file '{}' by user {}: {}", fileName, actorId, e.getMessage());
            throw new MediaOperationException("File conversion failed: " + e.getMessage(), e);
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
        
        // Set the media as the course thumbnail (bidirectional relationship)
        course.setThumbnail(media);
        media.getCoursesAsThumbnail().add(course);
        
        // Save both sides to ensure consistency
        mediaRepository.save(media);
        courseRepository.save(course);
        
        log.info("Media {} attached to course {} as thumbnail successfully (thumbnail_media_id={})", 
                mediaId, courseId, media.getId());
        
        return mediaMapper.toDto(media);
    }

    @Override
    @Transactional
    public MediaDTO attachToLesson(Long mediaId, Long lessonId, Long actorId) {
        log.info("Attaching media {} to lesson {} by user {}", mediaId, lessonId, actorId);
        
        Media media = getMediaOrThrow(mediaId);
        Lesson lesson = getLessonOrThrow(lessonId);
        
        // Check permissions - user must be owner of file or author/admin of course
        ensureOwnerOrAdmin(actorId, media.getUploadedBy(), lesson.getModule().getCourse().getAuthor().getId());
        
        // Set the media as the lesson video (bidirectional relationship)
        lesson.setVideoMedia(media);
        media.getLessonsAsVideo().add(lesson);
        
        // Save both sides to ensure consistency
        mediaRepository.save(media);
        lessonRepository.save(lesson);
        
        log.info("Media {} attached to lesson {} as video successfully (video_media_id={})", 
                mediaId, lessonId, media.getId());
        
        return mediaMapper.toDto(media);
    }

    @Override
    @Transactional
    public void detach(Long mediaId, Long actorId) {
        log.info("Detaching media {} from course/lesson by user {}", mediaId, actorId);
        
        Media media = getMediaOrThrow(mediaId);
        ensureOwnerOrAdmin(actorId, media.getUploadedBy(), null);
        
        int coursesUpdated = 0;
        int lessonsUpdated = 0;
        
        // Find and remove from courses using this as thumbnail
        List<Course> coursesWithThumbnail = courseRepository.findAll().stream()
            .filter(c -> c.getThumbnail() != null && c.getThumbnail().getId().equals(mediaId))
            .collect(java.util.stream.Collectors.toList());
        
        for (Course course : coursesWithThumbnail) {
            course.setThumbnail(null);
            courseRepository.save(course);
            coursesUpdated++;
            log.info("Removed media {} from course {} thumbnail", mediaId, course.getId());
        }
        
        // Find and remove from lessons using this as video
        List<Lesson> lessonsWithVideo = lessonRepository.findAll().stream()
            .filter(l -> l.getVideoMedia() != null && l.getVideoMedia().getId().equals(mediaId))
            .collect(java.util.stream.Collectors.toList());
        
        for (Lesson lesson : lessonsWithVideo) {
            lesson.setVideoMedia(null);
            lessonRepository.save(lesson);
            lessonsUpdated++;
            log.info("Removed media {} from lesson {} video", mediaId, lesson.getId());
        }
        
        // Clear the bidirectional collections in Media entity
        media.getCoursesAsThumbnail().clear();
        media.getLessonsAsVideo().clear();
        mediaRepository.save(media);
        
        log.info("Media {} detached successfully from {} courses and {} lessons", 
                mediaId, coursesUpdated, lessonsUpdated);
    }

    @Override
    @Transactional
    public void delete(Long mediaId, Long actorId) {
        log.info("Deleting media {} by user {}", mediaId, actorId);
        
        Media media = getMediaOrThrow(mediaId);
        ensureOwnerOrAdmin(actorId, media.getUploadedBy(), null);
        
        try {
            // Delete from Cloudinary if public ID exists
            if (media.getCloudinaryPublicId() != null) {
                String resourceType = media.getCloudinaryResourceType();
                if (resourceType == null) {
                    // Fallback: determine resource type from content type
                    if (isImage(media.getType())) {
                        resourceType = "image";
                    } else if (isVideo(media.getType())) {
                        resourceType = "video";
                    } else {
                        resourceType = "raw";
                    }
                }
                
                log.debug("Deleting file from Cloudinary: publicId={}, resourceType={}", 
                         media.getCloudinaryPublicId(), resourceType);
                cloudinaryService.deleteFile(media.getCloudinaryPublicId(), resourceType);
            } else {
                log.warn("Media {} has no Cloudinary public ID, skipping cloud deletion", mediaId);
            }
            
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
            // Use Cloudinary signed URL if public ID exists
            if (media.getCloudinaryPublicId() != null) {
                String resourceType = media.getCloudinaryResourceType();
                if (resourceType == null) {
                    // Fallback: determine resource type from content type
                    if (isImage(media.getType())) {
                        resourceType = "image";
                    } else if (isVideo(media.getType())) {
                        resourceType = "video";
                    } else {
                        resourceType = "raw";
                    }
                }
                
                log.debug("Creating Cloudinary signed URL: publicId={}, resourceType={}", 
                         media.getCloudinaryPublicId(), resourceType);
                return cloudinaryService.generateSignedUrl(media.getCloudinaryPublicId(), resourceType);
            } else {
                log.warn("Media {} has no Cloudinary public ID, returning public URL", mediaId);
                return media.getUrl();
            }
            
        } catch (Exception e) {
            log.warn("Failed to create signed URL for media {}, returning public URL: {}", 
                    mediaId, e.getMessage());
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

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
    
    // ===== Cloudinary Helper Methods =====
    
    /**
     * Convert InputStream to Spring MultipartFile for Cloudinary upload
     */
    private MultipartFile convertToMultipartFile(InputStream inputStream, String fileName, 
                                                 String contentType, long fileSize) throws IOException {
        byte[] bytes = inputStream.readAllBytes();
        return new MockMultipartFile(
                "file",
                fileName,
                contentType,
                bytes
        );
    }
    
    /**
     * Check if content type is an image
     */
    private boolean isImage(String contentType) {
        return contentType != null && contentType.startsWith("image/");
    }
    
    /**
     * Check if content type is a video
     */
    private boolean isVideo(String contentType) {
        return contentType != null && contentType.startsWith("video/");
    }
}