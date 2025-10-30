package com.exe.skillverse_backend.business_service.service;

import com.exe.skillverse_backend.business_service.entity.JobPosting;
import com.exe.skillverse_backend.business_service.entity.enums.JobStatus;
import com.exe.skillverse_backend.business_service.repository.JobPostingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Scheduler to automatically close expired job postings
 * Runs daily at 1:00 AM
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class JobScheduler {

    private final JobPostingRepository jobPostingRepository;

    /**
     * Auto-close jobs that are OPEN and past their deadline
     * Cron: "0 0 1 * * ?" = At 01:00 AM every day
     */
    @Scheduled(cron = "0 0 1 * * ?")
    @Transactional
    public void autoCloseExpiredJobs() {
        log.info("Starting auto-close expired jobs task...");

        LocalDate today = LocalDate.now();
        List<JobPosting> expiredJobs = jobPostingRepository.findByStatusAndDeadlineBefore(JobStatus.OPEN, today);

        if (expiredJobs.isEmpty()) {
            log.info("No expired jobs found");
            return;
        }

        int closedCount = 0;
        for (JobPosting job : expiredJobs) {
            job.setStatus(JobStatus.CLOSED);
            jobPostingRepository.save(job);
            closedCount++;
            log.info("Auto-closed job ID: {} (title: {}, deadline: {})",
                    job.getId(), job.getTitle(), job.getDeadline());
        }

        log.info("Auto-close task completed. Closed {} jobs", closedCount);
    }
}
