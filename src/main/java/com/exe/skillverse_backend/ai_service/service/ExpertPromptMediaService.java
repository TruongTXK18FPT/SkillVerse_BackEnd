package com.exe.skillverse_backend.ai_service.service;

import org.springframework.web.multipart.MultipartFile;

public interface ExpertPromptMediaService {

    String uploadMedia(Long configId, MultipartFile file);

    void deleteMedia(Long configId);

    String updateMediaUrl(Long configId, String mediaUrl);
}
