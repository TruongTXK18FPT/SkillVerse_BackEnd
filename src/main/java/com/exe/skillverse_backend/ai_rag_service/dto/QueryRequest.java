package com.exe.skillverse_backend.ai_rag_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.Map;

@Data
public class QueryRequest {
    private String query;
    
    @JsonProperty("top_k")
    private Integer topK;
    
    private Map<String, Object> filters;
}
