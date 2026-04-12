package com.exe.skillverse_backend.ai_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for generating a personalized AI roadmap
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GenerateRoadmapRequest {

    public enum RoadmapMode {
        SKILL_BASED,
        CAREER_BASED
    }

    /**
     * User's learning goal (e.g., "Learn Spring Boot", "Become a Data Scientist")
     */
    @NotBlank(message = "Goal is required")
    @Size(min = 5, max = 500, message = "Goal must be between 5 and 500 characters")
    private String goal;

    /**
     * Expected duration (e.g., "3 months", "6 weeks", "1 year")
     */
    @NotBlank(message = "Duration is required")
    @Size(max = 50, message = "Duration must be less than 50 characters")
    private String duration;

    /**
     * User's experience level: "beginner", "intermediate", "advanced"
     */
    @NotBlank(message = "Experience level is required")
    @Size(max = 50, message = "Experience level must be less than 50 characters")
    private String experience;

    /**
     * Learning style preference (e.g., "project-based", "theoretical",
     * "video-based", "hands-on")
     */
    @NotBlank(message = "Learning style is required")
    @Size(max = 50, message = "Learning style must be less than 50 characters")
    private String style;

    @Size(max = 100, message = "Industry must be less than 100 characters")
    private String industry;

    @Size(max = 20, message = "Roadmap type must be short")
    private String roadmapType;

    @Size(min = 2, max = 200, message = "Target must be between 2 and 200 characters")
    private String target;

    @Size(max = 100, message = "Final objective must be less than 100 characters")
    private String finalObjective;

    @Size(max = 50, message = "Current level must be less than 50 characters")
    private String currentLevel;

    @Size(max = 50, message = "Desired duration must be less than 50 characters")
    private String desiredDuration;

    @Size(max = 150, message = "Background must be less than 150 characters")
    private String background;

    @Size(max = 50, message = "Daily time must be less than 50 characters")
    private String dailyTime;

    @Size(max = 100, message = "Learning style must be less than 100 characters")
    private String learningStyle;

    @Size(max = 100, message = "Target environment must be less than 100 characters")
    private String targetEnvironment;

    @Size(max = 100, message = "Location must be less than 100 characters")
    private String location;

    @Size(max = 50, message = "Priority must be less than 50 characters")
    private String priority;

    private List<String> toolPreferences;

    @Size(max = 100, message = "Difficulty concern must be less than 100 characters")
    private String difficultyConcern;

    private Boolean incomeGoal;

    private RoadmapMode roadmapMode;

    private String aiAgentMode;

    private String skillName;
    private String skillCategory;
    private String desiredDepth;
    private String learnerType;
    private String currentSkillLevel;
    private String learningGoal;
    private String dailyLearningTime;
    private String assessmentPreference;
    private String difficultyTolerance;
    private List<String> toolPreference;

    private String targetRole;
    private String careerTrack;
    private String targetSeniority;
    private String workMode;
    private String targetMarket;
    private String companyType;
    private String timelineToWork;
    private Boolean incomeExpectation;
    private String workExperience;
    private Boolean transferableSkills;
    private String confidenceLevel;

    public String getRoadmapType() {
        if (roadmapMode != null) {
            return roadmapMode == RoadmapMode.SKILL_BASED ? "skill" : "career";
        }
        return roadmapType;
    }

    private String normalizeDurationToken(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isBlank()) {
            return null;
        }

        String upper = value.toUpperCase();
        if (upper.equals("3M")) {
            return "3 tháng";
        }
        if (upper.equals("6M")) {
            return "6 tháng";
        }
        if (upper.equals("12M")) {
            return "12 tháng";
        }

        return value;
    }

    public String getTarget() {
        if (roadmapMode == RoadmapMode.SKILL_BASED) {
            if (skillName != null && !skillName.isBlank()) return skillName;
        }
        if (roadmapMode == RoadmapMode.CAREER_BASED) {
            if (targetRole != null && !targetRole.isBlank()) return targetRole;
        }
        return target;
    }

    public String getDailyTime() {
        if (dailyLearningTime != null && !dailyLearningTime.isBlank()) {
            return dailyLearningTime;
        }
        if (dailyTime != null && !dailyTime.isBlank()) {
            return dailyTime;
        }
        if (roadmapMode == RoadmapMode.SKILL_BASED || roadmapMode == RoadmapMode.CAREER_BASED) {
            return "1_HOUR";
        }
        return dailyTime;
    }

    public String getDesiredDuration() {
        if (desiredDuration != null && !desiredDuration.isBlank()) {
            return normalizeDurationToken(desiredDuration);
        }
        if (roadmapMode == RoadmapMode.CAREER_BASED) {
            if (timelineToWork != null && !timelineToWork.isBlank()) {
                return normalizeDurationToken(timelineToWork);
            }
            return "6 tháng";
        }
        if (roadmapMode == RoadmapMode.SKILL_BASED) {
            return "1 tháng";
        }
        if (timelineToWork != null && !timelineToWork.isBlank()) {
            return normalizeDurationToken(timelineToWork);
        }
        if (dailyLearningTime != null && !dailyLearningTime.isBlank()) {
            return "1 tháng";
        }
        return null;
    }

    public String getDuration() {
        String desired = getDesiredDuration();
        if (desired != null && !desired.isBlank()) {
            return desired;
        }
        return duration;
    }

    public String getCurrentLevel() {
        if (currentLevel != null && !currentLevel.isBlank()) return currentLevel;
        if (currentSkillLevel != null && !currentSkillLevel.isBlank()) return currentSkillLevel.toLowerCase();
        return null;
    }
}
