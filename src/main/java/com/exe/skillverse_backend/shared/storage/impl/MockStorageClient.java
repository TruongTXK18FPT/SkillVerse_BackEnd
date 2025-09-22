package com.exe.skillverse_backend.shared.storage.impl;

import com.exe.skillverse_backend.shared.storage.StorageClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.Duration;

/**
 * Mock implementation of StorageClient for development/testing
 * TODO: Replace with actual S3 or other cloud storage implementation in production
 */
@Slf4j
@Component
public class MockStorageClient implements StorageClient {

    @Value("${app.storage.base-url:http://localhost:8080/api/media}")
    private String baseUrl;

    @Override
    public String putObject(String key, InputStream data, String contentType, long contentLength) {
        log.info("Mock storage: uploading object with key '{}', type '{}', size {}", key, contentType, contentLength);
        
        // TODO: Implement actual file storage (local filesystem or cloud storage)
        // For now, return a mock URL
        String publicUrl = baseUrl + "/" + key;
        
        log.debug("Mock storage: object uploaded successfully, URL: {}", publicUrl);
        return publicUrl;
    }

    @Override
    public void deleteObject(String key) {
        log.info("Mock storage: deleting object with key '{}'", key);
        
        // TODO: Implement actual file deletion
        
        log.debug("Mock storage: object deleted successfully");
    }

    @Override
    public String createSignedGetUrl(String key, Duration ttl) {
        log.debug("Mock storage: creating signed URL for key '{}' with TTL {}", key, ttl);
        
        // TODO: Implement actual signed URL generation
        // For now, return the public URL with a timestamp parameter
        String signedUrl = baseUrl + "/" + key + "?expires=" + (System.currentTimeMillis() + ttl.toMillis());
        
        log.debug("Mock storage: signed URL created: {}", signedUrl);
        return signedUrl;
    }

    @Override
    public boolean objectExists(String key) {
        log.debug("Mock storage: checking if object exists with key '{}'", key);
        
        // TODO: Implement actual existence check
        // For now, always return true
        return true;
    }

    @Override
    public String getPublicUrl(String key) {
        log.debug("Mock storage: getting public URL for key '{}'", key);
        
        String publicUrl = baseUrl + "/" + key;
        log.debug("Mock storage: public URL: {}", publicUrl);
        return publicUrl;
    }
}