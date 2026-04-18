package com.exe.skillverse_backend.study_service.repository;

import com.exe.skillverse_backend.study_service.entity.StudySession;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface StudySessionRepository extends JpaRepository<StudySession, UUID> {
    List<StudySession> findByUserId(Long userId);
    List<StudySession> findByUserIdAndStartTimeBetween(Long userId, LocalDateTime start, LocalDateTime end);

    /**
     * Finds all sessions for a user that overlap with a given time range.
     * Two sessions overlap when: startA < endB AND startB < endA
     */
    @Query("SELECT s FROM StudySession s WHERE s.user.id = :userId " +
           "AND s.startTime < :endTime AND s.endTime > :startTime")
    List<StudySession> findOverlappingSessions(
            @Param("userId") Long userId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);
}
