package com.exe.skillverse_backend.business_service.repository;

import com.exe.skillverse_backend.business_service.entity.ReviewWindow;
import com.exe.skillverse_backend.business_service.entity.ReviewWindow.ReviewStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ReviewWindowRepository extends JpaRepository<ReviewWindow, Long> {
    Optional<ReviewWindow> findByApplicationId(Long applicationId);
    Optional<ReviewWindow> findByJobId(Long jobId);

    List<ReviewWindow> findByStatus(ReviewStatus status);

    @Query("SELECT rw FROM ReviewWindow rw WHERE rw.status = :status AND rw.deadline < :now")
    List<ReviewWindow> findExpiredByStatus(@Param("status") ReviewStatus status, @Param("now") LocalDateTime now);

    @Query("SELECT rw FROM ReviewWindow rw WHERE rw.status = 'ACTIVE' AND rw.deadline < :now")
    List<ReviewWindow> findActiveExpiredWindows(@Param("now") LocalDateTime now);

    @Query("SELECT rw FROM ReviewWindow rw WHERE rw.status = 'ACTIVE' AND rw.reminderSent = false AND rw.deadline < :reminderThreshold")
    List<ReviewWindow> findWindowsNeedingReminder(@Param("reminderThreshold") LocalDateTime reminderThreshold);
}
