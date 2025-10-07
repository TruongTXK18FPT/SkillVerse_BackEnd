package com.exe.skillverse_backend.shared.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * Service interface for Cloudinary media upload operations
 */
public interface CloudinaryService {
    
    /**
     * Upload an image file to Cloudinary
     * 
     * @param file The multipart file to upload
     * @param folder Optional folder path in Cloudinary (e.g., "courses", "profiles")
     * @return Map containing upload result with URL, public_id, etc.
     * @throws IOException if upload fails
     */
    Map<String, Object> uploadImage(MultipartFile file, String folder) throws IOException;
    
    /**
     * Upload a video file to Cloudinary
     * 
     * @param file The multipart file to upload
     * @param folder Optional folder path in Cloudinary
     * @return Map containing upload result with URL, public_id, etc.
     * @throws IOException if upload fails
     */
    Map<String, Object> uploadVideo(MultipartFile file, String folder) throws IOException;
    
    /**
     * Upload any file type to Cloudinary (raw files like PDFs, docs, etc.)
     * 
     * @param file The multipart file to upload
     * @param folder Optional folder path in Cloudinary
     * @return Map containing upload result with URL, public_id, etc.
     * @throws IOException if upload fails
     */
    Map<String, Object> uploadFile(MultipartFile file, String folder) throws IOException;
    
    /**
     * Delete a file from Cloudinary by its public ID
     * 
     * @param publicId The public ID of the file to delete
     * @param resourceType The resource type (image, video, raw)
     * @return Map containing deletion result
     * @throws IOException if deletion fails
     */
    Map<String, Object> deleteFile(String publicId, String resourceType) throws IOException;
    
    /**
     * Generate a signed URL for private files
     * 
     * @param publicId The public ID of the file
     * @param resourceType The resource type
     * @return Signed URL string
     */
    String generateSignedUrl(String publicId, String resourceType);
}
