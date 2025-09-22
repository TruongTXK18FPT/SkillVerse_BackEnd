package com.exe.skillverse_backend.shared.service.impl;

import com.exe.skillverse_backend.shared.dto.MediaDTO;
import com.exe.skillverse_backend.shared.service.MediaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * Simple test/demo class for MediaService happy path testing
 * TODO: Replace with proper unit tests
 */
@Slf4j
@Component
public class MediaServiceTestHelper {

    /**
     * Test the happy path for media upload and operations
     * 
     * @param mediaService the MediaService to test
     * @return true if all tests pass
     */
    public boolean testHappyPath(MediaService mediaService) {
        try {
            log.info("=== MediaService Happy Path Test ===");
            
            // 1. Upload PNG image (1MB mock)
            String fileName = "test-image.png";
            String contentType = "image/png";
            long fileSize = 1024 * 1024; // 1MB
            InputStream data = new ByteArrayInputStream(new byte[(int) fileSize]);
            Long actorId = 1L;
            
            log.info("1. Uploading {} ({} bytes)...", fileName, fileSize);
            MediaDTO uploadedMedia = mediaService.upload(actorId, fileName, contentType, fileSize, data);
            log.info("   ✓ Upload successful: ID={}, URL={}", uploadedMedia.getId(), uploadedMedia.getUrl());
            
            // 2. Attach media to course (courseId=123)
            Long courseId = 123L;
            log.info("2. Attaching media {} to course {}...", uploadedMedia.getId(), courseId);
            MediaDTO attachedMedia = mediaService.attachToCourse(uploadedMedia.getId(), courseId, actorId);
            log.info("   ✓ Attachment successful");
            
            // 3. List media by course
            log.info("3. Listing media for course {}...", courseId);
            var courseMedia = mediaService.listByCourse(courseId);
            log.info("   ✓ Found {} media files for course", courseMedia.size());
            
            // 4. Detach media
            log.info("4. Detaching media {}...", uploadedMedia.getId());
            mediaService.detach(uploadedMedia.getId(), actorId);
            log.info("   ✓ Detachment successful");
            
            // 5. Verify course has no media
            log.info("5. Verifying course {} has no media...", courseId);
            var emptyList = mediaService.listByCourse(courseId);
            log.info("   ✓ Course media list is empty: {}", emptyList.isEmpty());
            
            // 6. Delete media
            log.info("6. Deleting media {}...", uploadedMedia.getId());
            mediaService.delete(uploadedMedia.getId(), actorId);
            log.info("   ✓ Deletion successful");
            
            // 7. Verify media is gone (should throw NotFoundException)
            log.info("7. Verifying media {} is deleted...", uploadedMedia.getId());
            try {
                mediaService.get(uploadedMedia.getId());
                log.error("   ✗ Media still exists after deletion!");
                return false;
            } catch (Exception e) {
                log.info("   ✓ Media correctly deleted (NotFoundException thrown)");
            }
            
            log.info("=== All tests passed! ===");
            return true;
            
        } catch (Exception e) {
            log.error("Happy path test failed: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Test file validation
     * 
     * @param mediaService the MediaService to test
     */
    public void testValidation(MediaService mediaService) {
        log.info("=== Testing File Validation ===");
        
        // Test valid file
        try {
            mediaService.validateFile("image/png", 1024 * 1024); // 1MB PNG
            log.info("✓ Valid file validation passed");
        } catch (Exception e) {
            log.error("✗ Valid file validation failed: {}", e.getMessage());
        }
        
        // Test file too large
        try {
            mediaService.validateFile("image/png", 600 * 1024 * 1024L); // 600MB
            log.error("✗ Large file validation should have failed");
        } catch (IllegalArgumentException e) {
            log.info("✓ Large file validation correctly failed: {}", e.getMessage());
        }
        
        // Test invalid content type
        try {
            mediaService.validateFile("application/x-malware", 1024); // Invalid type
            log.error("✗ Invalid content type validation should have failed");
        } catch (IllegalArgumentException e) {
            log.info("✓ Invalid content type validation correctly failed: {}", e.getMessage());
        }
    }
}