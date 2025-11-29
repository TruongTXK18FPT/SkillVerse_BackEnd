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
     * @param file   The multipart file to upload
     * @param folder Optional folder path in Cloudinary (e.g., "courses",
     *               "profiles")
     * @return Map containing upload result with URL, public_id, etc.
     * @throws IOException if upload fails
     */
    Map<String, Object> uploadImage(MultipartFile file, String folder) throws IOException;

    /**
     * Upload a video file to Cloudinary
     * 
     * @param file   The multipart file to upload
     * @param folder Optional folder path in Cloudinary
     * @return Map containing upload result with URL, public_id, etc.
     * @throws IOException if upload fails
     */
    Map<String, Object> uploadVideo(MultipartFile file, String folder) throws IOException;

    /**
     * Upload any file type to Cloudinary (raw files like PDFs, docs, etc.)
     * 
     * @param file   The multipart file to upload
     * @param folder Optional folder path in Cloudinary
     * @return Map containing upload result with URL, public_id, etc.
     * @throws IOException if upload fails
     */
    Map<String, Object> uploadFile(MultipartFile file, String folder) throws IOException;

    /**
     * Upload a file with a specific public ID (custom filename) to Cloudinary
     * using raw resource type by default.
     *
     * @param file     The multipart file to upload
     * @param folder   Optional folder path in Cloudinary
     * @param publicId Desired public ID (filename without extension)
     * @return Map containing upload result
     * @throws IOException if upload fails
     */
    default Map<String, Object> uploadFileNamed(MultipartFile file, String folder, String publicId) throws IOException {
        return uploadFile(file, folder);
    }

    /**
     * Upload a PDF file ensuring inline-friendly delivery (image resource type with
     * format=pdf)
     */
    default Map<String, Object> uploadPdf(MultipartFile file, String folder) throws IOException {
        return uploadFile(file, folder);
    }

    /**
     * Delete a file from Cloudinary by its public ID
     * 
     * @param publicId     The public ID of the file to delete
     * @param resourceType The resource type (image, video, raw)
     * @return Map containing deletion result
     * @throws IOException if deletion fails
     */
    Map<String, Object> deleteFile(String publicId, String resourceType) throws IOException;

    /**
     * Generate a signed URL for private files
     * 
     * @param publicId     The public ID of the file
     * @param resourceType The resource type
     * @return Signed URL string
     */
    String generateSignedUrl(String publicId, String resourceType);

    /**
     * Fetch remote file bytes from Cloudinary using generated (optionally signed)
     * URL
     */
    default byte[] fetchFile(String publicId, String resourceType) throws IOException {
        String url = generateSignedUrl(publicId, resourceType);
        java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
                .build();
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(url))
                .GET()
                .build();
        java.net.http.HttpResponse<byte[]> response;
        try {
            response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while fetching file", e);
        }
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return response.body();
        }
        throw new IOException("Failed to fetch file from Cloudinary. HTTP status: " + response.statusCode());
    }
}
