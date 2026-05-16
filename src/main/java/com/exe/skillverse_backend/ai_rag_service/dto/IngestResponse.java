package com.exe.skillverse_backend.ai_rag_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class IngestResponse {
    private String status;
    
    @JsonProperty("chunks_created")
    private int chunksCreated;
    
    @JsonProperty("documents_processed")
    private int documentsProcessed;
}
