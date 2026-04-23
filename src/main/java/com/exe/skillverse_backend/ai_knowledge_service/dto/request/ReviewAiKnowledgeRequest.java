package com.exe.skillverse_backend.ai_knowledge_service.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReviewAiKnowledgeRequest {
    @NotNull
    private Boolean approved;

    private String reviewNote;
}
