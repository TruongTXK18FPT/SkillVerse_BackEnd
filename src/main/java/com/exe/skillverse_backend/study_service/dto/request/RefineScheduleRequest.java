package com.exe.skillverse_backend.study_service.dto.request;

import com.exe.skillverse_backend.study_service.dto.response.StudySessionResponse;
import java.util.List;
import lombok.Data;

@Data
public class RefineScheduleRequest {
    private List<StudySessionResponse> currentSchedule;
    private String userFeedback;
    private String originalGoal;
}
