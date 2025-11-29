package com.exe.skillverse_backend.ai_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SttResponse {
    private String text;
    private double confidence;
    private String source;
    private long durationMs;
}

