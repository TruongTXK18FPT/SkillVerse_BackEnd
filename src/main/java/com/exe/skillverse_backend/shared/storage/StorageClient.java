package com.exe.skillverse_backend.shared.storage;

import java.io.InputStream;
import java.time.Duration;

/**
 * Storage client interface for handling file operations
 * Supports both S3-like cloud storage and local file storage
 */
public interface StorageClient {

    /**
     * Upload a file to storage
     * 
     * @param key unique identifier for the file
     * @param data input stream of file data
     * @param contentType MIME type of the file
     * @param contentLength size of the file in bytes
     * @return public URL or signed URL to access the file
     */
    String putObject(String key, InputStream data, String contentType, long contentLength);

    /**
     * Delete a file from storage
     * 
     * @param key unique identifier for the file to delete
     */
    void deleteObject(String key);

    /**
     * Create a signed URL for temporary access to a file
     * 
     * @param key unique identifier for the file
     * @param ttl time-to-live for the signed URL
     * @return signed URL for temporary access
     */
    String createSignedGetUrl(String key, Duration ttl);

    /**
     * Check if a file exists in storage
     * 
     * @param key unique identifier for the file
     * @return true if file exists, false otherwise
     */
    boolean objectExists(String key);

    /**
     * Get the permanent public URL for a file (if storage supports it)
     * 
     * @param key unique identifier for the file
     * @return public URL to access the file
     */
    String getPublicUrl(String key);
}