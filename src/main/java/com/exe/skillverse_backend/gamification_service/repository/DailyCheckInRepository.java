package com.exe.skillverse_backend.gamification_service.repository;

import com.exe.skillverse_backend.gamification_service.entity.DailyCheckIn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DailyCheckInRepository extends JpaRepository<DailyCheckIn, Long> {

    /**
     * Check if user has already checked in today
     */
    boolean existsByUserIdAndCheckInDate(Long userId, LocalDate checkInDate);

    /**
     * Get user's check-in for a specific date
     */
    Optional<DailyCheckIn> findByUserIdAndCheckInDate(Long userId, LocalDate checkInDate);

    /**
     * Get all check-ins for a user within a date range (inclusive)
     */
    @Query("SELECT c FROM DailyCheckIn c WHERE c.userId = :userId " +
           "AND c.checkInDate >= :startDate AND c.checkInDate <= :endDate " +
           "ORDER BY c.checkInDate ASC")
    List<DailyCheckIn> findByUserIdAndDateRange(
        @Param("userId") Long userId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Get all check-in dates for a user, ordered by date descending
     */
    @Query("SELECT c.checkInDate FROM DailyCheckIn c WHERE c.userId = :userId " +
           "ORDER BY c.checkInDate DESC")
    List<LocalDate> findCheckInDatesByUserId(@Param("userId") Long userId);

    /**
     * Get check-in dates for a user since a specific date
     */
    @Query("SELECT c.checkInDate FROM DailyCheckIn c WHERE c.userId = :userId " +
           "AND c.checkInDate >= :since ORDER BY c.checkInDate ASC")
    List<LocalDate> findCheckInDatesByUserIdSince(
        @Param("userId") Long userId,
        @Param("since") LocalDate since
    );

    /**
     * Count total check-ins for a user
     */
    Long countByUserId(Long userId);

    /**
     * Count check-ins for a user in current month
     */
    @Query("SELECT COUNT(c) FROM DailyCheckIn c WHERE c.userId = :userId " +
           "AND c.checkInDate >= :startOfMonth AND c.checkInDate <= :endOfMonth")
    Long countByUserIdInMonth(
        @Param("userId") Long userId,
        @Param("startOfMonth") LocalDate startOfMonth,
        @Param("endOfMonth") LocalDate endOfMonth
    );

    /**
     * Get the most recent check-in for a user
     */
    Optional<DailyCheckIn> findTopByUserIdOrderByCheckInDateDesc(Long userId);

    /**
     * Get all users who checked in on a specific date (for leaderboard)
     */
    @Query("SELECT c.userId FROM DailyCheckIn c WHERE c.checkInDate = :date")
    List<Long> findUserIdsWhoCheckedInOnDate(@Param("date") LocalDate date);
}
