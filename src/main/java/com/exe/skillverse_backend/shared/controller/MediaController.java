package com.exe.skillverse_backend.shared.controller;

import com.exe.skillverse_backend.shared.dto.MediaDTO;
import com.exe.skillverse_backend.shared.dto.PageResponse;
import com.exe.skillverse_backend.shared.service.MediaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "Media Management", description = "APIs for managing media files (upload, attach, delete)")
public class MediaController {

    private final MediaService mediaService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
        summary = "Upload a media file",
        description = "Upload a new media file (image, video, document, etc.)"
    )
    @ApiResponse(responseCode = "201", description = "File uploaded successfully")
    @ApiResponse(responseCode = "400", description = "Invalid file or request")
    public ResponseEntity<MediaDTO> uploadFile(
            @Parameter(description = "Media file to upload") @RequestParam("file") MultipartFile file,
            @Parameter(description = "ID of the user uploading") @RequestParam("actorId") Long actorId) throws IOException {
        
        log.info("Uploading file: {} by user: {}", file.getOriginalFilename(), actorId);
        
        // Validate file
        if (file.isEmpty()) {
            throw new IllegalArgumentException("FILE_IS_EMPTY");
        }
        
        MediaDTO mediaDto = mediaService.upload(
            actorId,
            file.getOriginalFilename(),
            file.getContentType(),
            file.getSize(),
            file.getInputStream()
        );
        
        return ResponseEntity.status(HttpStatus.CREATED).body(mediaDto);
    }

    @PutMapping("/{mediaId}/attach-course")
    @Operation(summary = "Attach media to a course")
    public ResponseEntity<MediaDTO> attachToCourse(
            @Parameter(description = "Media ID") @PathVariable @NotNull Long mediaId,
            @Parameter(description = "Course ID") @RequestParam @NotNull Long courseId,
            @Parameter(description = "Actor ID") @RequestParam @NotNull Long actorId) {
        
        log.info("Attaching media {} to course {} by user {}", mediaId, courseId, actorId);
        MediaDTO updated = mediaService.attachToCourse(mediaId, courseId, actorId);
        return ResponseEntity.ok(updated);
    }

    @PutMapping("/{mediaId}/attach-lesson")
    @Operation(summary = "Attach media to a lesson")
    public ResponseEntity<MediaDTO> attachToLesson(
            @Parameter(description = "Media ID") @PathVariable @NotNull Long mediaId,
            @Parameter(description = "Lesson ID") @RequestParam @NotNull Long lessonId,
            @Parameter(description = "Actor ID") @RequestParam @NotNull Long actorId) {
        
        log.info("Attaching media {} to lesson {} by user {}", mediaId, lessonId, actorId);
        MediaDTO updated = mediaService.attachToLesson(mediaId, lessonId, actorId);
        return ResponseEntity.ok(updated);
    }

    @PutMapping("/{mediaId}/detach")
    @Operation(summary = "Detach media from course/lesson")
    public ResponseEntity<Void> detachMedia(
            @Parameter(description = "Media ID") @PathVariable @NotNull Long mediaId,
            @Parameter(description = "Actor ID") @RequestParam @NotNull Long actorId) {
        
        log.info("Detaching media {} by user {}", mediaId, actorId);
        mediaService.detach(mediaId, actorId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{mediaId}")
    @Operation(summary = "Delete a media file")
    public ResponseEntity<Void> deleteMedia(
            @Parameter(description = "Media ID") @PathVariable @NotNull Long mediaId,
            @Parameter(description = "Actor ID") @RequestParam @NotNull Long actorId) {
        
        log.info("Deleting media {} by user {}", mediaId, actorId);
        mediaService.delete(mediaId, actorId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{mediaId}")
    @Operation(summary = "Get media file metadata")
    public ResponseEntity<MediaDTO> getMedia(
            @Parameter(description = "Media ID") @PathVariable @NotNull Long mediaId) {
        
        MediaDTO media = mediaService.get(mediaId);
        return ResponseEntity.ok(media);
    }

    @GetMapping("/{mediaId}/signed-url")
    @Operation(summary = "Get signed URL for accessing media file")
    public ResponseEntity<Map<String, String>> getSignedUrl(
            @Parameter(description = "Media ID") @PathVariable @NotNull Long mediaId,
            @Parameter(description = "Actor ID") @RequestParam @NotNull Long actorId) {
        
        String signedUrl = mediaService.getSignedUrl(mediaId, actorId);
        return ResponseEntity.ok(Map.of("url", signedUrl));
    }

    @GetMapping("/owner/{ownerId}")
    @Operation(summary = "List media files by owner")
    public ResponseEntity<PageResponse<MediaDTO>> getMediaByOwner(
            @Parameter(description = "Owner user ID") @PathVariable @NotNull Long ownerId,
            @PageableDefault(size = 20) Pageable pageable) {
        
        PageResponse<MediaDTO> result = mediaService.listByOwner(ownerId, pageable);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/course/{courseId}")
    @Operation(summary = "List media files associated with a course")
    public ResponseEntity<List<MediaDTO>> getMediaByCourse(
            @Parameter(description = "Course ID") @PathVariable @NotNull Long courseId) {
        
        List<MediaDTO> media = mediaService.listByCourse(courseId);
        return ResponseEntity.ok(media);
    }

    @GetMapping("/lesson/{lessonId}")
    @Operation(summary = "List media files associated with a lesson")
    public ResponseEntity<List<MediaDTO>> getMediaByLesson(
            @Parameter(description = "Lesson ID") @PathVariable @NotNull Long lessonId) {
        
        List<MediaDTO> media = mediaService.listByLesson(lessonId);
        return ResponseEntity.ok(media);
    }

    @GetMapping("/search")
    @Operation(summary = "Search media files by filename")
    public ResponseEntity<PageResponse<MediaDTO>> searchMedia(
            @Parameter(description = "Search query") @RequestParam String query,
            @PageableDefault(size = 20) Pageable pageable) {
        
        PageResponse<MediaDTO> results = mediaService.searchByFileName(query, pageable);
        return ResponseEntity.ok(results);
    }

    @PostMapping("/validate")
    @Operation(summary = "Validate file before upload")
    public ResponseEntity<Map<String, String>> validateFile(
            @Parameter(description = "Content type") @RequestParam String contentType,
            @Parameter(description = "File size") @RequestParam Long fileSize) {
        
        try {
            mediaService.validateFile(contentType, fileSize);
            return ResponseEntity.ok(Map.of("status", "valid", "message", "File is valid"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                .body(Map.of("status", "invalid", "message", e.getMessage()));
        }
    }

    @GetMapping("/types")
    @Operation(summary = "Get supported media types")
    public ResponseEntity<Map<String, Object>> getSupportedTypes() {
        // This would typically come from configuration
        Map<String, Object> supportedTypes = Map.of(
            "images", List.of("image/jpeg", "image/png", "image/gif", "image/webp"),
            "videos", List.of("video/mp4", "video/avi", "video/mov", "video/webm"),
            "documents", List.of("application/pdf", "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            "maxSizeBytes", 100 * 1024 * 1024, // 100MB
            "maxSizeMB", 100
        );
        
        return ResponseEntity.ok(supportedTypes);
    }
}