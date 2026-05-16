package com.exe.skillverse_backend.ai_rag_service.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class EmbeddingRequest {
    private String model;
    private List<String> input;
}
