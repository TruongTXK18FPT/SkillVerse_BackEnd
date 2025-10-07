package com.exe.skillverse_backend.shared.service.impl;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.exe.skillverse_backend.shared.service.CloudinaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Implementation of CloudinaryService for media upload operations
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CloudinaryServiceImpl implements CloudinaryService {

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
        log.info("Uploading image: {} to folder: {}", file.getOriginalFilename(), folder);
        
        validateFile(file, "image");
        
        Map<String, Object> params = buildUploadParams(folder, "image");
        params.put("transformation", new com.cloudinary.Transformation()
                .quality("auto")
                .fetchFormat("auto"));
        
        Map<String, Object> result = cloudinary.uploader().upload(file.getBytes(), params);
        
        log.info("Image uploaded successfully. Public ID: {}, URL: {}", 
                result.get("public_id"), result.get("secure_url"));
        
        return result;
    }

    @Override
    public Map<String, Object> uploadVideo(MultipartFile file, String folder) throws IOException {
        log.info("Uploading video: {} to folder: {}", file.getOriginalFilename(), folder);
        
        validateFile(file, "video");
        
        Map<String, Object> params = buildUploadParams(folder, "video");
        params.put("chunk_size", 6000000); // 6MB chunks for large videos
        
        Map<String, Object> result = cloudinary.uploader().upload(file.getBytes(), params);
        
        log.info("Video uploaded successfully. Public ID: {}, URL: {}", 
                result.get("public_id"), result.get("secure_url"));
        
        return result;
    }

    @Override
    public Map<String, Object> uploadFile(MultipartFile file, String folder) throws IOException {
        log.info("Uploading file: {} to folder: {}", file.getOriginalFilename(), folder);
        
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
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
     * Validate file before upload
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
    }
}
