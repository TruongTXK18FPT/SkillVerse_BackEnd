package com.exe.skillverse_backend.gamification_service.repository;

import com.exe.skillverse_backend.gamification_service.entity.GamificationLeaderboardSnapshot;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface GamificationLeaderboardSnapshotRepository extends JpaRepository<GamificationLeaderboardSnapshot, Long> {
    
    @Query("SELECT s FROM GamificationLeaderboardSnapshot s WHERE s.leaderboardPeriod = :period " +
           "AND s.leaderboardType = :type AND s.snapshotDate = " +
           "(SELECT MAX(s2.snapshotDate) FROM GamificationLeaderboardSnapshot s2 WHERE s2.leaderboardPeriod = :period AND s2.leaderboardType = :type) " +
           "ORDER BY s.rankPosition ASC")
    Page<GamificationLeaderboardSnapshot> findLatestLeaderboard(
        @Param("period") String period, 
        @Param("type") String type, 
        Pageable pageable);
    
    @Query("SELECT s FROM GamificationLeaderboardSnapshot s WHERE s.userId = :userId " +
           "AND s.leaderboardPeriod = :period AND s.leaderboardType = :type " +
           "ORDER BY s.snapshotDate DESC LIMIT 1")
    Optional<GamificationLeaderboardSnapshot> findUserLatestRank(
        @Param("userId") Long userId, 
        @Param("period") String period, 
        @Param("type") String type);
    
    @Query("SELECT s FROM GamificationLeaderboardSnapshot s WHERE s.leaderboardPeriod = :period " +
           "AND s.leaderboardType = :type AND s.snapshotDate >= :since " +
           "ORDER BY s.snapshotDate DESC, s.rankPosition ASC")
    List<GamificationLeaderboardSnapshot> findLeaderboardHistory(
        @Param("period") String period, 
        @Param("type") String type, 
        @Param("since") LocalDateTime since);
}
