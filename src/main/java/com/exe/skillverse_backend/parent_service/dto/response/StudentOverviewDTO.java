package com.exe.skillverse_backend.parent_service.dto.response;

import com.exe.skillverse_backend.auth_service.dto.response.UserDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentOverviewDTO {
    private UserDto studentInfo;
    private int completedCourses;
    private int inProgressCourses;
    private double overallProgress; // Percentage
    private int streakDays;
    private String learningStatus; // "Good", "Behind", "Risk"
    
    // New fields
    private String premiumPlan;
    private String premiumExpiry;
    private long studyTimeToday; // Minutes
    private long studyTimeWeek; // Minutes
    private long studyTimeMonth; // Minutes
    private int totalRoadmaps;
    private int chatSessionsCount;
    private int completedJobs;
    private boolean portfolioCreated;
}
