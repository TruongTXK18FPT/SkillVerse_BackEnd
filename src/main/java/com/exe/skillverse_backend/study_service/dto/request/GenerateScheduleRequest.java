package com.exe.skillverse_backend.study_service.dto.request;

import lombok.Data;
import java.time.LocalDate;
import java.util.List;

@Data
public class GenerateScheduleRequest {
    private String subjectName;
    private String freeTimeDescription; // e.g. "Monday 8-10am, Wednesday 2-4pm"
    private int durationMinutes;
    private LocalDate deadline;
    private LocalDate startDate;
    private String timezone;
    private List<String> preferredDays; // e.g. ["Mon","Wed","Fri"]
    private List<String> preferredTimeWindows; // e.g. ["08:00-10:00","14:00-16:00"]
    private List<String> topics; // e.g. ["Data Structures","Algorithms"]
    private String desiredOutcome; // e.g. "Ôn thi giữa kỳ đạt 8+"
    private String intensityLevel; // e.g. "low","medium","high"
    private Integer breakMinutesBetweenSessions; // e.g. 15
    private Integer maxSessionsPerDay; // e.g. 2
    private String studyMethod; // e.g. "Pomodoro","Active Recall"
    private String resourcesPreference; // e.g. "Sách giáo trình, video ngắn"
    private String studyPreference; // e.g. "morning","afternoon","evening","night","custom"
    private Boolean avoidLateNight; // true to avoid sessions 23:00-06:00
    private Boolean allowLateNight; // allow late night if user chooses night
    private Boolean confirmLateNight; // user confirmation required when late night detected
    private String earliestStartLocalTime; // e.g. "06:00"
    private String latestEndLocalTime; // e.g. "22:00"
    private Integer maxDailyStudyMinutes; // e.g. 240
    private String chronotype; // e.g. "lark","owl","neutral"
    private List<String> idealFocusWindows; // e.g. ["07:00-10:00","19:00-21:00"]
}
