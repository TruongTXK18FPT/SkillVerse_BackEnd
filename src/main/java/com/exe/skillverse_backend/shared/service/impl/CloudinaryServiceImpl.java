package com.exe.skillverse_backend.shared.service.impl;

import com.cloudinary.Cloudinary;
import com.cloudinary.Transformation;
import com.cloudinary.utils.ObjectUtils;
import com.exe.skillverse_backend.shared.service.CloudinaryService;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Implementation of CloudinaryService for media upload operations.
 * All size limits are aligned with Cloudinary Free Tier:
 * - Image: 10MB
 * - Raw (PDF, DOCX, etc.): 10MB
 * - Video: 100MB
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CloudinaryServiceImpl implements CloudinaryService {

    // Cloudinary Free Tier upload limits
    private static final long IMAGE_MAX_SIZE = 10 * 1024 * 1024L;   // 10MB
    private static final long RAW_MAX_SIZE   = 10 * 1024 * 1024L;   // 10MB
    private static final long VIDEO_MAX_SIZE = 100 * 1024 * 1024L;  // 100MB
    // Chunked upload threshold (Cloudinary requires uploadLarge for files > 100MB)
    private static final long VIDEO_CHUNKED_THRESHOLD = 100 * 1024 * 1024L; // 100MB

    // Allowed raw file content types
    private static final Set<String> ALLOWED_RAW_TYPES = Set.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/plain",
            "text/markdown",
            "text/x-markdown",
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    private final Cloudinary cloudinary;

    @Value("${cloudinary.upload.folder:skillverse}")
    private String baseFolder;

    @Value("${cloudinary.upload.use-filename:true}")
    private boolean useFilename;

    @Value("${cloudinary.upload.unique-filename:true}")
    private boolean uniqueFilename;

    @Value("${cloudinary.upload.overwrite:false}")
    private boolean overwrite;

    @Override
    public Map<String, Object> uploadImage(MultipartFile file, String folder) throws IOException {
        return uploadImageWithOptions(file, folder, null);
    }

    @Override
    public Map<String, Object> uploadImageWithOptions(MultipartFile file, String folder, Map<String, Object> options) throws IOException {
        log.info("Uploading image: {} to folder: {}", file.getOriginalFilename(), folder);

        validateFile(file, "image");

        Map<String, Object> params = buildUploadParams(folder, "image");
        params.put("transformation", new Transformation()
                .quality("auto")
                .fetchFormat("auto"));
        
        if (options != null) {
            params.putAll(options);
        }

        Map<String, Object> result = cloudinary.uploader().upload(file.getBytes(), params);

        log.info("Image uploaded successfully. Public ID: {}, URL: {}",
                result.get("public_id"), result.get("secure_url"));

        return result;
    }

    @Override
    public Map<String, Object> renameFile(String fromPublicId, String toPublicId, String resourceType) throws IOException {
        log.info("Renaming file from public ID: {} to: {} (resource type: {})", fromPublicId, toPublicId, resourceType);

        if (fromPublicId == null || fromPublicId.trim().isEmpty()) {
            throw new IllegalArgumentException("Source public ID cannot be null or empty");
        }
        if (toPublicId == null || toPublicId.trim().isEmpty()) {
            throw new IllegalArgumentException("Target public ID cannot be null or empty");
        }

        Map<String, Object> params = ObjectUtils.asMap(
                "resource_type", resourceType,
                "overwrite", true
        );

        Map<String, Object> result = cloudinary.uploader().rename(fromPublicId, toPublicId, params);
        log.info("Rename successful. New public ID: {}, URL: {}", result.get("public_id"), result.get("secure_url"));
        return result;
    }

    @Override
    public Map<String, Object> uploadVideo(MultipartFile file, String folder) throws IOException {
        long fileSizeBytes = file.getSize();
        double fileSizeMB = fileSizeBytes / (1024.0 * 1024.0);

        log.info("[VIDEO_UPLOAD] Starting upload: filename={}, size={}MB ({}bytes), folder={}",
                file.getOriginalFilename(), String.format("%.2f", fileSizeMB), fileSizeBytes, folder);

        validateFile(file, "video");
        log.debug("[VIDEO_UPLOAD] File validation passed");

        Map<String, Object> params = buildUploadParams(folder, "video");
        params.put("resource_type", "video");

        Map<String, Object> result;

        // Use uploadLarge for files > 100MB (Cloudinary API requirement)
        // uploadLarge uses chunked upload which is mandatory for files > 100MB
        if (fileSizeBytes > VIDEO_CHUNKED_THRESHOLD) {
            log.info("[VIDEO_UPLOAD] Large file detected ({}MB > {}MB), using chunked upload (uploadLarge)",
                    String.format("%.2f", fileSizeMB), VIDEO_CHUNKED_THRESHOLD / 1024 / 1024);
            params.put("chunk_size", 6000000); // 6MB chunks

            try {
                // Use InputStream instead of byte array to avoid OutOfMemoryError
                result = cloudinary.uploader().uploadLarge(file.getInputStream(), params);
                log.info("[VIDEO_UPLOAD] Chunked upload completed successfully");
            } catch (Exception e) {
                log.error("[VIDEO_UPLOAD] Chunked upload failed: {}", e.getMessage(), e);
                throw new IOException("Failed to upload large video: " + e.getMessage(), e);
            }
        } else {
            log.info("[VIDEO_UPLOAD] Standard file ({}MB <= 100MB), using direct upload",
                    String.format("%.2f", fileSizeMB));

            try {
                result = cloudinary.uploader().upload(file.getBytes(), params);
                log.info("[VIDEO_UPLOAD] Direct upload completed successfully");
            } catch (Exception e) {
                log.error("[VIDEO_UPLOAD] Direct upload failed: {}", e.getMessage(), e);
                throw new IOException("Failed to upload video: " + e.getMessage(), e);
            }
        }

        // Extract and log upload results
        String publicId = (String) result.get("public_id");
        String secureUrl = (String) result.get("secure_url");
        Object duration = result.get("duration");
        Object format = result.get("format");

        log.info("[VIDEO_UPLOAD] Upload successful! publicId={}, url={}, duration={}s, format={}",
                publicId, secureUrl, duration, format);
        log.debug("[VIDEO_UPLOAD] Full result: {}", result);

        return result;
    }

    @Override
    public Map<String, Object> uploadFile(MultipartFile file, String folder) throws IOException {
        log.info("Uploading file: {} to folder: {}", file.getOriginalFilename(), folder);

        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        String contentType = file.getContentType();
        if (contentType == null) {
            throw new IllegalArgumentException("File content type is null");
        }

        if (file.getSize() > RAW_MAX_SIZE) {
            throw new IllegalArgumentException(
                    String.format("File too large. Maximum allowed size is %dMB",
                            RAW_MAX_SIZE / 1024 / 1024));
        }

        if (!ALLOWED_RAW_TYPES.contains(contentType)) {
            throw new IllegalArgumentException(
                    String.format("Invalid file type: %s. Allowed: %s", contentType, ALLOWED_RAW_TYPES));
        }

        Map<String, Object> params = buildUploadParams(folder, "raw");

        Map<String, Object> result = cloudinary.uploader().upload(file.getBytes(), params);

        log.info("File uploaded successfully. Public ID: {}, URL: {}",
                result.get("public_id"), result.get("secure_url"));

        return result;
    }

    @Override
    public Map<String, Object> deleteFile(String publicId, String resourceType) throws IOException {
        log.info("Deleting file with public ID: {} and resource type: {}", publicId, resourceType);

        if (publicId == null || publicId.trim().isEmpty()) {
            throw new IllegalArgumentException("Public ID cannot be null or empty");
        }

        Map<String, Object> params = ObjectUtils.asMap("resource_type", resourceType);
        Map<String, Object> result = cloudinary.uploader().destroy(publicId, params);

        log.info("File deletion result: {}", result);

        return result;
    }

    @Override
    public String generateSignedUrl(String publicId, String resourceType) {
        log.debug("Generating signed URL for public ID: {}", publicId);

        if (publicId == null || publicId.trim().isEmpty()) {
            throw new IllegalArgumentException("Public ID cannot be null or empty");
        }

        // Generate a signed URL - Note: Basic signed URLs don't expire in Cloudinary
        // For time-limited URLs, use the Advanced URL delivery with expiration tokens
        return cloudinary.url()
                .resourceType(resourceType)
                .signed(true)
                .generate(publicId);
    }

    /**
     * Build upload parameters map
     */
    private Map<String, Object> buildUploadParams(String folder, String resourceType) {
        Map<String, Object> params = new HashMap<>();

        // Set folder path
        String fullFolder = folder != null && !folder.isEmpty()
                ? baseFolder + "/" + folder
                : baseFolder;
        params.put("folder", fullFolder);

        // Set resource type
        params.put("resource_type", resourceType);

        // Set filename options
        params.put("use_filename", useFilename);
        params.put("unique_filename", uniqueFilename);
        params.put("overwrite", overwrite);

        return params;
    }

    /**
     * Validate file type and size before upload
     */
    private void validateFile(MultipartFile file, String expectedType) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        String contentType = file.getContentType();
        if (contentType == null) {
            throw new IllegalArgumentException("File content type is null");
        }

        if (!contentType.startsWith(expectedType + "/")) {
            throw new IllegalArgumentException(
                    String.format("Invalid file type. Expected %s but got %s", expectedType, contentType));
        }

        // Validate file size
        long fileSize = file.getSize();
        if ("image".equals(expectedType) && fileSize > IMAGE_MAX_SIZE) {
            throw new IllegalArgumentException(
                    String.format("Image file too large. Maximum allowed size is %dMB",
                            IMAGE_MAX_SIZE / 1024 / 1024));
        }
        if ("video".equals(expectedType) && fileSize > VIDEO_MAX_SIZE) {
            throw new IllegalArgumentException(
                    String.format("Video file too large. Maximum allowed size is %dMB",
                            VIDEO_MAX_SIZE / 1024 / 1024));
        }
    }
}
