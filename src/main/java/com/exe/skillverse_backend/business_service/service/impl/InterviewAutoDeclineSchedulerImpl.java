package com.exe.skillverse_backend.business_service.service.impl;

import com.exe.skillverse_backend.business_service.entity.InterviewSchedule;
import com.exe.skillverse_backend.business_service.entity.InterviewSchedule.CancelledBy;
import com.exe.skillverse_backend.business_service.entity.InterviewSchedule.InterviewStatus;
import com.exe.skillverse_backend.business_service.entity.JobApplication;
import com.exe.skillverse_backend.business_service.entity.enums.JobApplicationStatus;
import com.exe.skillverse_backend.business_service.repository.InterviewScheduleRepository;
import com.exe.skillverse_backend.business_service.repository.JobApplicationRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class InterviewAutoDeclineSchedulerImpl {

    private static final String TIMEOUT_REASON = "Ứng viên không xác nhận lịch phỏng vấn đúng hạn.";

    private final InterviewScheduleRepository interviewScheduleRepository;
    private final JobApplicationRepository jobApplicationRepository;

    /**
     * Auto-decline pending interviews that passed response deadline.
     * Runs every 5 minutes.
     */
    @Scheduled(initialDelay = 10000, fixedRate = 300000)
    @Transactional
    public void autoDeclineExpiredPendingInterviews() {
        LocalDateTime now = LocalDateTime.now();
        List<InterviewSchedule> expired = interviewScheduleRepository
                .findByStatusAndResponseDeadlineAtLessThanEqual(InterviewStatus.PENDING, now);

        if (expired.isEmpty()) {
            return;
        }

        log.info("Found {} pending interviews past response deadline", expired.size());
        for (InterviewSchedule interview : expired) {
            try {
                processExpiredInterview(interview, now);
            } catch (Exception e) {
                log.error("Failed to auto-decline interview {}: {}", interview.getId(), e.getMessage(), e);
            }
        }
    }

    private void processExpiredInterview(InterviewSchedule interview, LocalDateTime now) {
        if (interview.getStatus() != InterviewStatus.PENDING) {
            return;
        }

        interview.setStatus(InterviewStatus.CANCELLED);
        interview.setRespondedAt(now);
        interview.setCancelledBy(CancelledBy.AUTO);
        interview.setCancelReason(TIMEOUT_REASON);
        interviewScheduleRepository.save(interview);

        JobApplication application = interview.getApplication();
        application.setStatus(JobApplicationStatus.REJECTED);
        application.setRejectionReason(TIMEOUT_REASON);
        application.setProcessedAt(now);
        jobApplicationRepository.save(application);

        log.info("Auto-declined interview {} and rejected application {}", interview.getId(), application.getId());
    }
}
