package com.exe.skillverse_backend.gamification_service.service;

import com.exe.skillverse_backend.gamification_service.dto.response.CheckInResponseDTO;
import com.exe.skillverse_backend.gamification_service.dto.response.StreakInfoDTO;

/**
 * Service interface for daily check-in and streak management
 */
public interface DailyCheckInService {

    /**
     * Perform daily check-in for user
     * @param userId The user ID
     * @return CheckInResponseDTO with check-in result and rewards
     */
    CheckInResponseDTO checkIn(Long userId);

    /**
     * Get streak information for user
     * @param userId The user ID
     * @return StreakInfoDTO with streak details
     */
    StreakInfoDTO getStreakInfo(Long userId);

    /**
     * Check if user has checked in today
     * @param userId The user ID
     * @return true if already checked in today
     */
    boolean hasCheckedInToday(Long userId);

    /**
     * Calculate current streak for user
     * @param userId The user ID
     * @return Current streak count
     */
    int calculateCurrentStreak(Long userId);

    /**
     * Calculate longest streak for user
     * @param userId The user ID
     * @return Longest streak count
     */
    int calculateLongestStreak(Long userId);
}
