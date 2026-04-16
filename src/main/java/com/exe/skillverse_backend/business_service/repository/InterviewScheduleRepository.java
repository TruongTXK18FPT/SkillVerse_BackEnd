package com.exe.skillverse_backend.business_service.repository;

import com.exe.skillverse_backend.business_service.entity.InterviewSchedule;
import com.exe.skillverse_backend.business_service.entity.InterviewSchedule.InterviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface InterviewScheduleRepository extends JpaRepository<InterviewSchedule, Long> {

    Optional<InterviewSchedule> findByApplicationId(Long applicationId);

    boolean existsByApplicationId(Long applicationId);

    @Query("SELECT COUNT(i) > 0 FROM InterviewSchedule i WHERE i.application.id = :applicationId AND i.status NOT IN (:excludedStatuses)")
    boolean existsByApplicationIdExcludingStatuses(
            @Param("applicationId") Long applicationId,
            @Param("excludedStatuses") List<InterviewStatus> excludedStatuses);

    @Query("SELECT i FROM InterviewSchedule i WHERE i.application.jobPosting.id = :jobPostingId ORDER BY i.scheduledAt DESC")
    List<InterviewSchedule> findByJobPostingId(@Param("jobPostingId") Long jobPostingId);

    @Query("SELECT i FROM InterviewSchedule i WHERE i.application.user.id = :userId ORDER BY i.scheduledAt DESC")
    List<InterviewSchedule> findByUserId(@Param("userId") Long userId);

    @Query("SELECT i FROM InterviewSchedule i WHERE i.id = :interviewId AND i.application.user.id = :userId")
    Optional<InterviewSchedule> findByIdAndApplicationUserId(
            @Param("interviewId") Long interviewId,
            @Param("userId") Long userId);

    List<InterviewSchedule> findByStatusAndResponseDeadlineAtLessThanEqual(
            InterviewStatus status,
            LocalDateTime responseDeadlineAt);
}