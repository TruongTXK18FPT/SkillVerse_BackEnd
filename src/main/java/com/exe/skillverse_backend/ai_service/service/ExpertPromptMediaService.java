package com.exe.skillverse_backend.ai_service.service;

import com.exe.skillverse_backend.ai_service.entity.ExpertPromptConfig;
import com.exe.skillverse_backend.ai_service.repository.ExpertPromptConfigRepository;
import com.exe.skillverse_backend.shared.exception.ApiException;
import com.exe.skillverse_backend.shared.exception.ErrorCode;
import com.exe.skillverse_backend.shared.service.CloudinaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * Service for managing media (icons/images) for Expert Prompt Configs
 * Uses Cloudinary for storage
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExpertPromptMediaService {

    private final ExpertPromptConfigRepository expertPromptConfigRepository;
    private final CloudinaryService cloudinaryService;

    /**
     * Upload media for an expert prompt config
     * 
     * @param configId ID of the expert prompt config
     * @param file Media file to upload
     * @return URL of the uploaded media
     */
    @Transactional
    public String uploadMedia(Long configId, MultipartFile file) {
        // Validate file
        if (file == null || file.isEmpty()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Media file is required");
        }

        // Find config
        ExpertPromptConfig config = expertPromptConfigRepository.findById(configId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, 
                    "Expert prompt config not found with id: " + configId));

        try {
            // Upload to Cloudinary with folder structure: expert-prompts/{domain}/{industry}
            String folder = String.format("expert-prompts/%s/%s", 
                sanitizeFolderName(config.getDomain()),
                sanitizeFolderName(config.getIndustry()));
            
            Map<String, Object> uploadResult = cloudinaryService.uploadImage(file, folder);
            String mediaUrl = (String) uploadResult.get("secure_url");
            
            // Delete old media if exists (extract publicId from old URL if needed)
            if (config.getMediaUrl() != null && !config.getMediaUrl().isEmpty()) {
                try {
                    // Extract publicId from URL or use stored value
                    // For now, we'll skip deletion to avoid errors
                    log.info("Old media exists, consider manual cleanup: {}", config.getMediaUrl());
                } catch (Exception e) {
                    log.warn("Failed to delete old media: {}", e.getMessage());
                }
            }
            
            // Update config with new media URL
            config.setMediaUrl(mediaUrl);
            expertPromptConfigRepository.save(config);
            
            log.info("Uploaded media for expert config {}: {}", configId, mediaUrl);
            return mediaUrl;
            
        } catch (IOException e) {
            log.error("Failed to upload media for config {}: {}", configId, e.getMessage());
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, 
                "Failed to upload media: " + e.getMessage());
        }
    }

    /**
     * Delete media for an expert prompt config
     * 
     * @param configId ID of the expert prompt config
     */
    @Transactional
    public void deleteMedia(Long configId) {
        ExpertPromptConfig config = expertPromptConfigRepository.findById(configId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, 
                    "Expert prompt config not found with id: " + configId));

        if (config.getMediaUrl() == null || config.getMediaUrl().isEmpty()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "No media to delete");
        }

        // For deletion, we need publicId which we don't store separately
        // So we'll just clear the URL from database
        // Admin can manually delete from Cloudinary dashboard if needed
        config.setMediaUrl(null);
        expertPromptConfigRepository.save(config);
        
        log.info("Cleared media URL for expert config {}. Manual Cloudinary cleanup may be needed.", configId);
    }

    /**
     * Update media URL directly (for admin to paste Cloudinary URL)
     * 
     * @param configId ID of the expert prompt config
     * @param mediaUrl New media URL
     * @return Updated media URL
     */
    @Transactional
    public String updateMediaUrl(Long configId, String mediaUrl) {
        ExpertPromptConfig config = expertPromptConfigRepository.findById(configId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, 
                    "Expert prompt config not found with id: " + configId));

        config.setMediaUrl(mediaUrl);
        expertPromptConfigRepository.save(config);
        
        log.info("Updated media URL for expert config {}: {}", configId, mediaUrl);
        return mediaUrl;
    }

    /**
     * Sanitize folder name for Cloudinary (remove special characters)
     */
    private String sanitizeFolderName(String name) {
        if (name == null) return "general";
        return name.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .trim();
    }
}
