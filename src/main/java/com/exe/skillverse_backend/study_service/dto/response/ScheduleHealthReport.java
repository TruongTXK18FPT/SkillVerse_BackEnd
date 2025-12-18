package com.exe.skillverse_backend.study_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleHealthReport {
    private boolean healthy;
    private List<String> warnings;
    private List<String> errors;
    private List<String> suggestions;
    private List<StudySessionResponse> adjustedSessions;
    private List<SessionScore> sessionScores;
}
