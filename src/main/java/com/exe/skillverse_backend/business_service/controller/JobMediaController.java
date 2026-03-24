package com.exe.skillverse_backend.business_service.controller;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.shared.service.CloudinaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller for handling job-related media uploads.
 * Specifically for uploading images embedded in job descriptions.
 */
@RestController
@RequestMapping("/api/short-term-jobs")
@RequiredArgsConstructor
@Slf4j
public class JobMediaController {

    private final CloudinaryService cloudinaryService;
    private final UserRepository userRepository;

    private static final String JOB_IMAGES_FOLDER = "job-images";
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    /**
     * Upload an image for use in a job description.
     * Returns the Cloudinary URL which can be embedded as ![alt](url) in markdown.
     */
    @PostMapping("/upload-image")
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<?> uploadJobImage(@RequestParam("file") MultipartFile file) {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            return ResponseEntity.badRequest().body(Map.of("error", "File size exceeds 10MB limit"));
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Only image files are allowed"));
        }

        try {
            String folder = JOB_IMAGES_FOLDER + "/" + userId;
            Map<String, Object> result = cloudinaryService.uploadImage(file, folder);
            String url = (String) result.get("secure_url");
            String publicId = (String) result.get("public_id");

            Map<String, Object> response = new HashMap<>();
            response.put("url", url);
            response.put("publicId", publicId);
            response.put("originalFilename", file.getOriginalFilename());
            response.put("size", file.getSize());
            response.put("contentType", contentType);

            log.info("Job image uploaded successfully: userId={}, publicId={}", userId, publicId);
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            log.error("Failed to upload job image: userId={}, error={}", userId, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to upload image: " + e.getMessage()));
        }
    }

    /**
     * Delete a job image from Cloudinary.
     */
    @DeleteMapping("/images/{publicId}")
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<?> deleteJobImage(@PathVariable String publicId) {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        try {
            // Only allow deletion of images in user's own folder
            String expectedPrefix = JOB_IMAGES_FOLDER + "/" + userId + "/";
            if (!publicId.startsWith(expectedPrefix)) {
                return ResponseEntity.status(403).body(Map.of("error", "Cannot delete images from other users"));
            }

            cloudinaryService.deleteFile(publicId, "image");
            log.info("Job image deleted: userId={}, publicId={}", userId, publicId);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (IOException e) {
            log.error("Failed to delete job image: userId={}, publicId={}, error={}", userId, publicId, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to delete image: " + e.getMessage()));
        }
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }

        String principalName = auth.getName();
        try {
            return Long.parseLong(principalName);
        } catch (NumberFormatException ignored) {
            User user = userRepository.findByEmail(principalName).orElse(null);
            return user != null ? user.getId() : null;
        }
    }
}
