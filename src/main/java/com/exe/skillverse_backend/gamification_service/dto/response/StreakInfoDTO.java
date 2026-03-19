package com.exe.skillverse_backend.gamification_service.dto.response;

import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StreakInfoDTO {
    
    // Current streak count
    private Integer currentStreak;
    
    // Longest streak ever
    private Integer longestStreak;
    
    // Total check-in days
    private Integer totalCheckIns;
    
    // Check-ins this month
    private Integer monthlyCheckIns;
    
    // Weekly activity (Mon-Sun) - true if checked in
    private List<Boolean> weeklyActivity;
    
    // Power level percentage (0-100)
    private Integer powerLevel;
    
    // Has user checked in today?
    private boolean checkedInToday;
    
    // Last check-in date
    private LocalDate lastCheckInDate;
    
    // Current week dates (Mon-Sun)
    private List<LocalDate> weekDates;
}
