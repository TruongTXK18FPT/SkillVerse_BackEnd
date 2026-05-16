package com.exe.skillverse_backend.ai_rag_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.Map;

@Data
public class ContextResult {
    private String source;
    
    @JsonProperty("doc_id")
    private String docId;
    
    @JsonProperty("doc_type")
    private String docType;
    
    private String content;
    private double score;
    private Map<String, Object> metadata;
}
