package com.exe.skillverse_backend.study_service.dto.request;

import java.time.LocalDate;
import java.util.List;
import lombok.Data;

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

    /**
     * Module IDs of the course(s) suggested for this roadmap node.
     * Used by AiStudySupportServiceImpl to load module + lesson content
     * for the AI Study Planner prompt.
     *
     * <p>When set, AiStudySupportServiceImpl fetches actual module titles,
     * lesson titles, and content from the course_service layer and injects
     * them into the prompt so the AI generates sessions based on real course content.
     *
     * <p>Populated by {@code JourneyServiceImpl.createStudyPlanForRoadmapNode()}
     * when the roadmap node has suggestedModuleIds from course matching.
     */
    private List<String> suggestedModuleIds;

    /**
     * Course module + lesson content context for the AI Study Planner.
     * Built lazily by {@code AiStudySupportServiceImpl.buildCourseModulesContext()}
     * when suggestedModuleIds is set.
     * Contains a formatted text block with module titles, lesson titles,
     * durations, and content summaries.
     */
    private String courseModulesContext;
}
