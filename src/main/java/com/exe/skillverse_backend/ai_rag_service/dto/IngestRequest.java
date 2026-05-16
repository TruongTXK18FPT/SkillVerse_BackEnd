package com.exe.skillverse_backend.ai_rag_service.dto;

import lombok.Data;
import java.util.List;

@Data
public class IngestRequest {
    private List<DocumentInput> documents;
}
