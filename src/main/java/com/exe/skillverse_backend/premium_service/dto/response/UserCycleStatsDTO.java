package com.exe.skillverse_backend.premium_service.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    private List<Boolean> weeklyActivity;
    private LocalDateTime cycleStartDate;
    private LocalDateTime cycleEndDate;
}
