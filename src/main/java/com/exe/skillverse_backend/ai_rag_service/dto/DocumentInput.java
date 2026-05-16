package com.exe.skillverse_backend.ai_rag_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

@Data
public class DocumentInput {
    @JsonProperty("doc_id")
    private String docId;
    
    @JsonProperty("doc_type")
    private String docType;
    
    private String title;
    private String content;
    
    private Map<String, Object> metadata;
}
