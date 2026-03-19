package com.exe.skillverse_backend.gamification_service.dto.response;

import java.time.LocalDate;
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
public class CheckInResponseDTO {
    
    private boolean success;
    private String message;
    
    // Today's check-in info
    private LocalDate checkInDate;
    private LocalDateTime checkInTime;
    private boolean alreadyCheckedIn;
    
    // Rewards
    private Integer coinsAwarded;
    private Integer xpAwarded;
    private boolean isBonusDay;
    
    // Streak info
    private Integer currentStreak;
    private Integer longestStreak;
    private Integer streakDay; // Which day of streak (1-7)
    
    // Weekly activity (Mon-Sun)
    private List<Boolean> weeklyActivity;
    
    // Power level (based on weekly check-ins)
    private Integer powerLevel; // 0-100%
}
