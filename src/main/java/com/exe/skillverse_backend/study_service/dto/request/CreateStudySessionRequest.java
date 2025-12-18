package com.exe.skillverse_backend.study_service.dto.request;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CreateStudySessionRequest {
    private String title;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String description;
}
