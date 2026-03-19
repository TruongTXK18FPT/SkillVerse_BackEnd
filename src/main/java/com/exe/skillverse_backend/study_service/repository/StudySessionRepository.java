package com.exe.skillverse_backend.study_service.repository;

import com.exe.skillverse_backend.study_service.entity.StudySession;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StudySessionRepository extends JpaRepository<StudySession, UUID> {
    List<StudySession> findByUserId(Long userId);
    List<StudySession> findByUserIdAndStartTimeBetween(Long userId, LocalDateTime start, LocalDateTime end);
}
