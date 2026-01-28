package com.exe.skillverse_backend.gamification_service.service.impl;

import com.exe.skillverse_backend.gamification_service.dto.response.CheckInResponseDTO;
import com.exe.skillverse_backend.gamification_service.dto.response.StreakInfoDTO;
import com.exe.skillverse_backend.gamification_service.entity.DailyCheckIn;
import com.exe.skillverse_backend.gamification_service.repository.DailyCheckInRepository;
import com.exe.skillverse_backend.gamification_service.service.DailyCheckInService;
import com.exe.skillverse_backend.gamification_service.service.GamificationWalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Implementation of DailyCheckInService for streak tracking
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DailyCheckInServiceImpl implements DailyCheckInService {

    private final DailyCheckInRepository checkInRepository;
    private final GamificationWalletService walletService;

    // Reward constants
    private static final int BASE_COINS = 5;
    private static final int BASE_XP = 10;
    private static final int BONUS_COINS = 20; // Day 7 bonus
    private static final int BONUS_XP = 50;    // Day 7 bonus

    @Override
    @Transactional
    public CheckInResponseDTO checkIn(Long userId) {
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        // Check if already checked in today
        if (hasCheckedInToday(userId)) {
            StreakInfoDTO streakInfo = getStreakInfo(userId);
            return CheckInResponseDTO.builder()
                    .success(false)
                    .message("Bạn đã điểm danh hôm nay rồi!")
                    .alreadyCheckedIn(true)
                    .checkInDate(today)
                    .currentStreak(streakInfo.getCurrentStreak())
                    .longestStreak(streakInfo.getLongestStreak())
                    .weeklyActivity(streakInfo.getWeeklyActivity())
                    .powerLevel(streakInfo.getPowerLevel())
                    .build();
        }

        // Calculate current streak before this check-in
        int currentStreak = calculateCurrentStreak(userId);
        
        // If yesterday was checked in, continue streak; otherwise start fresh
        boolean continuedStreak = false;
        Optional<DailyCheckIn> lastCheckIn = checkInRepository.findTopByUserIdOrderByCheckInDateDesc(userId);
        if (lastCheckIn.isPresent()) {
            LocalDate lastDate = lastCheckIn.get().getCheckInDate();
            if (lastDate.equals(today.minusDays(1))) {
                continuedStreak = true;
                currentStreak++; // This check-in continues the streak
            } else if (!lastDate.equals(today)) {
                currentStreak = 1; // Streak broken, start fresh
            }
        } else {
            currentStreak = 1; // First ever check-in
        }

        // Calculate streak day (1-7 for weekly cycle)
        int streakDay = ((currentStreak - 1) % 7) + 1;
        boolean isBonusDay = streakDay == 7;

        // Calculate rewards
        int coinsAwarded = isBonusDay ? BONUS_COINS : BASE_COINS;
        int xpAwarded = isBonusDay ? BONUS_XP : BASE_XP;

        // Create check-in record
        DailyCheckIn checkIn = DailyCheckIn.builder()
                .userId(userId)
                .checkInDate(today)
                .checkInTime(now)
                .coinsAwarded(coinsAwarded)
                .xpAwarded(xpAwarded)
                .streakDay(streakDay)
                .isBonusDay(isBonusDay)
                .build();

        checkInRepository.save(checkIn);
        log.info("User {} checked in on {}. Streak: {}, Day: {}, Bonus: {}", 
                userId, today, currentStreak, streakDay, isBonusDay);

        // Award coins and XP
        try {
            String reason = isBonusDay 
                    ? "Điểm danh ngày " + streakDay + " (Bonus tuần!)" 
                    : "Điểm danh ngày " + streakDay;
            walletService.awardCoins(userId, coinsAwarded, xpAwarded, "CHECK_IN", checkIn.getCheckInId(), reason);
        } catch (Exception e) {
            log.warn("Failed to award coins for check-in: {}", e.getMessage());
        }

        // Get updated streak info
        StreakInfoDTO streakInfo = getStreakInfo(userId);

        String message = isBonusDay 
                ? "🎉 Chúc mừng! Bạn đã điểm danh 7 ngày liên tiếp và nhận được phần thưởng bonus!"
                : "✅ Điểm danh thành công! Chuỗi học tập: " + currentStreak + " ngày";

        return CheckInResponseDTO.builder()
                .success(true)
                .message(message)
                .checkInDate(today)
                .checkInTime(now)
                .alreadyCheckedIn(false)
                .coinsAwarded(coinsAwarded)
                .xpAwarded(xpAwarded)
                .isBonusDay(isBonusDay)
                .currentStreak(streakInfo.getCurrentStreak())
                .longestStreak(streakInfo.getLongestStreak())
                .streakDay(streakDay)
                .weeklyActivity(streakInfo.getWeeklyActivity())
                .powerLevel(streakInfo.getPowerLevel())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public StreakInfoDTO getStreakInfo(Long userId) {
        LocalDate today = LocalDate.now();
        
        // Get current week (Monday to Sunday)
        LocalDate startOfWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate endOfWeek = startOfWeek.plusDays(6);
        
        // Build week dates list
        List<LocalDate> weekDates = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            weekDates.add(startOfWeek.plusDays(i));
        }

        // Get check-ins for this week
        List<DailyCheckIn> weekCheckIns = checkInRepository.findByUserIdAndDateRange(
                userId, startOfWeek, endOfWeek);
        List<LocalDate> checkedInDates = weekCheckIns.stream()
                .map(DailyCheckIn::getCheckInDate)
                .collect(Collectors.toList());

        // Build weekly activity array (Mon-Sun)
        List<Boolean> weeklyActivity = weekDates.stream()
                .map(checkedInDates::contains)
                .collect(Collectors.toList());

        // Calculate power level (based on weekly check-ins)
        int checkedDaysThisWeek = (int) weeklyActivity.stream().filter(b -> b).count();
        int powerLevel = Math.round((checkedDaysThisWeek / 7.0f) * 100);

        // Get other stats
        int currentStreak = calculateCurrentStreak(userId);
        int longestStreak = calculateLongestStreak(userId);
        boolean checkedInToday = hasCheckedInToday(userId);
        
        Long totalCheckIns = checkInRepository.countByUserId(userId);
        Long monthlyCheckIns = checkInRepository.countByUserIdInMonth(
                userId, 
                today.withDayOfMonth(1), 
                today.withDayOfMonth(today.lengthOfMonth()));

        Optional<DailyCheckIn> lastCheckIn = checkInRepository.findTopByUserIdOrderByCheckInDateDesc(userId);

        return StreakInfoDTO.builder()
                .currentStreak(currentStreak)
                .longestStreak(longestStreak)
                .totalCheckIns(totalCheckIns != null ? totalCheckIns.intValue() : 0)
                .monthlyCheckIns(monthlyCheckIns != null ? monthlyCheckIns.intValue() : 0)
                .weeklyActivity(weeklyActivity)
                .powerLevel(powerLevel)
                .checkedInToday(checkedInToday)
                .lastCheckInDate(lastCheckIn.map(DailyCheckIn::getCheckInDate).orElse(null))
                .weekDates(weekDates)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasCheckedInToday(Long userId) {
        return checkInRepository.existsByUserIdAndCheckInDate(userId, LocalDate.now());
    }

    @Override
    @Transactional(readOnly = true)
    public int calculateCurrentStreak(Long userId) {
        List<LocalDate> checkInDates = checkInRepository.findCheckInDatesByUserId(userId);
        
        if (checkInDates.isEmpty()) {
            return 0;
        }

        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        // Check if most recent check-in is today or yesterday
        LocalDate mostRecent = checkInDates.get(0);
        if (!mostRecent.equals(today) && !mostRecent.equals(yesterday)) {
            return 0; // Streak is broken
        }

        int streak = 1;
        for (int i = 1; i < checkInDates.size(); i++) {
            LocalDate prev = checkInDates.get(i - 1);
            LocalDate curr = checkInDates.get(i);
            
            if (prev.minusDays(1).equals(curr)) {
                streak++;
            } else {
                break;
            }
        }

        return streak;
    }

    @Override
    @Transactional(readOnly = true)
    public int calculateLongestStreak(Long userId) {
        List<LocalDate> checkInDates = checkInRepository.findCheckInDatesByUserId(userId);
        
        if (checkInDates.isEmpty()) {
            return 0;
        }

        // Sort ascending for easier calculation
        checkInDates = checkInDates.stream()
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toList());

        int longestStreak = 1;
        int currentStreak = 1;

        for (int i = 1; i < checkInDates.size(); i++) {
            LocalDate prev = checkInDates.get(i - 1);
            LocalDate curr = checkInDates.get(i);
            
            if (prev.plusDays(1).equals(curr)) {
                currentStreak++;
            } else {
                if (currentStreak > longestStreak) {
                    longestStreak = currentStreak;
                }
                currentStreak = 1;
            }
        }

        return Math.max(longestStreak, currentStreak);
    }
}
