package com.exe.skillverse_backend.premium_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCycleStatsDTO {
    private Integer enrolledCoursesCount;
    private Integer completedCoursesCount;
    private Integer completedProjectsCount;
    private Integer certificatesCount;
    private Integer totalHoursStudied;
    private Integer currentStreak;
    private Integer longestStreak;
    private java.util.List<Boolean> weeklyActivity;
    private LocalDateTime cycleStartDate;
    private LocalDateTime cycleEndDate;
}
