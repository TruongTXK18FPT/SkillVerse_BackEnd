package com.exe.skillverse_backend.study_service.dto.request;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class CreateStudySessionRequest {
    private String title;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String description;
}
