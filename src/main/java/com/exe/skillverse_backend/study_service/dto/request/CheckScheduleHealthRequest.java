package com.exe.skillverse_backend.study_service.dto.request;

import com.exe.skillverse_backend.study_service.dto.response.StudySessionResponse;
import java.util.List;
import lombok.Data;

@Data
public class CheckScheduleHealthRequest {
    private List<StudySessionResponse> sessions;
    private String timezone;
    private String earliestStartLocalTime; // e.g. "06:00"
    private String latestEndLocalTime; // e.g. "22:00"
    private Integer maxDailyStudyMinutes; // e.g. 240
    private Integer breakMinutesBetweenSessions; // e.g. 15
    private String studyPreference; // morning/afternoon/evening/night/custom
    private String chronotype; // e.g. "lark","owl","neutral"
    private List<String> idealFocusWindows; // e.g. ["07:00-10:00","19:00-21:00"]
}
