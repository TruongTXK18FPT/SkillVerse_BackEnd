package com.exe.skillverse_backend.ai_knowledge_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class AdminChatbotKnowledgeUploadRequest {
    @NotNull
    private MultipartFile file;

    @NotBlank
    private String title;

    private String description;

    private String industry;

    private String level;
}
