package com.exe.skillverse_backend.shared.controller;

import com.exe.skillverse_backend.shared.service.CloudinaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * REST Controller for media upload operations using Cloudinary
 */
@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Media Upload", description = "APIs for uploading and managing media files via Cloudinary")
public class MediaUploadController {

    private final CloudinaryService cloudinaryService;

    @PostMapping("/upload/image")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Upload an image file", description = "Upload an image to Cloudinary (supports JPG, PNG, GIF, etc.)")
    public ResponseEntity<?> uploadImage(
            @Parameter(description = "Image file to upload") @RequestParam("file") MultipartFile file,
            @Parameter(description = "Optional folder name") @RequestParam(required = false) String folder) {

        try {
            log.info("Uploading image: {}", file.getOriginalFilename());
            Map<String, Object> result = cloudinaryService.uploadImage(file, folder);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Image uploaded successfully");
            response.put("url", result.get("secure_url"));
            response.put("publicId", result.get("public_id"));
            response.put("format", result.get("format"));
            response.put("width", result.get("width"));
            response.put("height", result.get("height"));
            response.put("bytes", result.get("bytes"));

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.error("Invalid image upload request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()));
        } catch (IOException e) {
            log.error("Failed to upload image", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Failed to upload image: " + e.getMessage()));
        }
    }

    @PostMapping("/upload/video")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Upload a video file", description = "Upload a video to Cloudinary (supports MP4, MOV, AVI, etc.)")
    public ResponseEntity<?> uploadVideo(
            @Parameter(description = "Video file to upload") @RequestParam("file") MultipartFile file,
            @Parameter(description = "Optional folder name") @RequestParam(required = false) String folder) {

        try {
            log.info("Uploading video: {}", file.getOriginalFilename());
            Map<String, Object> result = cloudinaryService.uploadVideo(file, folder);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Video uploaded successfully");
            response.put("url", result.get("secure_url"));
            response.put("publicId", result.get("public_id"));
            response.put("format", result.get("format"));
            response.put("duration", result.get("duration"));
            response.put("bytes", result.get("bytes"));

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.error("Invalid video upload request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()));
        } catch (IOException e) {
            log.error("Failed to upload video", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Failed to upload video: " + e.getMessage()));
        }
    }

    @PostMapping("/upload/file")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Upload any file", description = "Upload any file type to Cloudinary (PDF, DOC, etc.)")
    public ResponseEntity<?> uploadFile(
            @Parameter(description = "File to upload") @RequestParam("file") MultipartFile file,
            @Parameter(description = "Optional folder name") @RequestParam(required = false) String folder) {

        try {
            log.info("Uploading file: {}", file.getOriginalFilename());
            Map<String, Object> result = cloudinaryService.uploadFile(file, folder);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "File uploaded successfully");
            response.put("url", result.get("secure_url"));
            response.put("publicId", result.get("public_id"));
            response.put("format", result.get("format"));
            response.put("bytes", result.get("bytes"));

            return ResponseEntity.ok(response);
        } catch (IOException e) {
            log.error("Failed to upload file", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Failed to upload file: " + e.getMessage()));
        }
    }

    @DeleteMapping("/delete/{publicId}")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('MENTOR')")
    @Operation(summary = "Delete a file", description = "Delete a file from Cloudinary by its public ID")
    public ResponseEntity<?> deleteFile(
            @Parameter(description = "Public ID of the file to delete") @PathVariable String publicId,
            @Parameter(description = "Resource type (image, video, raw)") @RequestParam(defaultValue = "image") String resourceType) {

        try {
            log.info("Deleting file with public ID: {}", publicId);
            Map<String, Object> result = cloudinaryService.deleteFile(publicId, resourceType);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "File deleted successfully");
            response.put("result", result.get("result"));

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.error("Invalid delete request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()));
        } catch (IOException e) {
            log.error("Failed to delete file", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Failed to delete file: " + e.getMessage()));
        }
    }

    @GetMapping("/signed-url/{publicId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get signed URL", description = "Generate a temporary signed URL for accessing a private file")
    public ResponseEntity<?> getSignedUrl(
            @Parameter(description = "Public ID of the file") @PathVariable String publicId,
            @Parameter(description = "Resource type (image, video, raw)") @RequestParam(defaultValue = "image") String resourceType) {

        try {
            String signedUrl = cloudinaryService.generateSignedUrl(publicId, resourceType);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("signedUrl", signedUrl);
            response.put("expiresIn", "1 hour");

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.error("Invalid signed URL request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()));
        }
    }
}
