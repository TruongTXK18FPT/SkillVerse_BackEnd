package com.exe.skillverse_backend.ai_rag_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class QueryResponse {
    private List<ContextResult> contexts;
    
    @JsonProperty("query_time_ms")
    private long queryTimeMs;
}
