package com.exe.skillverse_backend.ai_service.dto.request;

import lombok.Data;

@Data
public class TtsRequest {
    private String text;
    private String voice; // optional: e.g., "banmai"
    private Double speed; // optional: -2..2
}

