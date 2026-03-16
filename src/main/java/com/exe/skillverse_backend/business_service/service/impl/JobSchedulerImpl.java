package com.exe.skillverse_backend.business_service.service.impl;

import com.exe.skillverse_backend.business_service.entity.JobApplication;
import com.exe.skillverse_backend.business_service.entity.JobPosting;
import com.exe.skillverse_backend.business_service.entity.ShortTermJob;
import com.exe.skillverse_backend.business_service.entity.ShortTermJobApplication;
import com.exe.skillverse_backend.business_service.entity.enums.JobApplicationStatus;
import com.exe.skillverse_backend.business_service.entity.enums.JobStatus;
import com.exe.skillverse_backend.business_service.entity.enums.ShortTermApplicationStatus;
import com.exe.skillverse_backend.business_service.entity.enums.ShortTermJobStatus;
import com.exe.skillverse_backend.business_service.repository.JobApplicationRepository;
import com.exe.skillverse_backend.business_service.repository.JobPostingRepository;
import com.exe.skillverse_backend.business_service.repository.ShortTermJobApplicationRepository;
import com.exe.skillverse_backend.business_service.repository.ShortTermJobRepository;
import com.exe.skillverse_backend.premium_service.service.RecruiterSubscriptionService;
import com.exe.skillverse_backend.shared.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduler for automatic job posting management
 * - Auto-close expired jobs
 * - Auto-approve premium recruiter jobs (after 1 day)
 * - Auto-reject non-premium recruiter jobs (after 1 week)
 * - Auto-reject applications when job deadline passes
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class JobSchedulerImpl {

    private final JobPostingRepository jobPostingRepository;
    private final ShortTermJobRepository shortTermJobRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final ShortTermJobApplicationRepository shortTermApplicationRepository;
    private final RecruiterSubscriptionService recruiterSubscriptionService;
    private final EmailService emailService;

    // Time thresholds
    private static final int PREMIUM_APPROVAL_DAYS = 1;
    private static final int NORMAL_APPROVAL_DAYS = 7;

    // ==================== LONG-TERM JOB SCHEDULERS ====================

    /**
     * Auto-close long-term jobs that are OPEN and past their deadline
     * Runs daily at 1:00 AM
     */
    @Scheduled(cron = "0 0 1 * * ?")
    @Transactional
    public void autoCloseExpiredLongTermJobs() {
        log.info("Starting auto-close expired long-term jobs task...");

        LocalDate today = LocalDate.now();
        List<JobPosting> expiredJobs = jobPostingRepository.findByStatusAndDeadlineBefore(JobStatus.OPEN, today);

        if (expiredJobs.isEmpty()) {
            log.info("No expired long-term jobs found");
            return;
        }

        int closedCount = 0;
        for (JobPosting job : expiredJobs) {
            job.setStatus(JobStatus.CLOSED);
            jobPostingRepository.save(job);
            closedCount++;
            log.info("Auto-closed long-term job ID: {} (title: {}, deadline: {})",
                    job.getId(), job.getTitle(), job.getDeadline());
        }

        log.info("Auto-close long-term jobs task completed. Closed {} jobs", closedCount);
    }

    /**
     * Auto-approve or auto-reject pending long-term jobs based on recruiter subscription
     * - Premium recruiter: auto-approve after 1 day
     * - Normal recruiter: auto-reject after 1 week
     * Runs daily at 2:00 AM
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void autoProcessPendingLongTermJobs() {
        log.info("Starting auto-process pending long-term jobs task...");

        LocalDateTime now = LocalDateTime.now();
        List<JobPosting> pendingJobs = jobPostingRepository.findByStatusAndCreatedAtBefore(
                JobStatus.PENDING_APPROVAL, now.minusDays(1));

        if (pendingJobs.isEmpty()) {
            log.info("No pending long-term jobs found");
            return;
        }

        int approvedCount = 0;
        int rejectedCount = 0;

        for (JobPosting job : pendingJobs) {
            Long recruiterUserId = job.getRecruiterProfile().getUser().getId();
            LocalDateTime createdAt = job.getCreatedAt();
            long daysWaiting = java.time.Duration.between(createdAt, now).toDays();

            boolean hasPremium = recruiterSubscriptionService.hasActiveRecruiterSubscription(recruiterUserId);

            if (hasPremium && daysWaiting >= PREMIUM_APPROVAL_DAYS) {
                // Auto-approve for premium recruiter after 1 day
                job.setStatus(JobStatus.OPEN);
                jobPostingRepository.save(job);
                approvedCount++;
                log.info("Auto-approved premium recruiter long-term job ID: {} (waiting {} days)",
                        job.getId(), daysWaiting);

                // Send approval email notification
                try {
                    emailService.sendJobApprovalNotification(job.getRecruiterProfile().getUser().getEmail(),
                            job.getTitle(), "Long-term job approved");
                } catch (Exception e) {
                    log.error("Failed to send approval email for job ID: {}", job.getId(), e);
                }
            } else if (!hasPremium && daysWaiting >= NORMAL_APPROVAL_DAYS) {
                // Auto-reject for non-premium recruiter after 1 week
                job.setStatus(JobStatus.REJECTED);
                jobPostingRepository.save(job);
                rejectedCount++;
                log.info("Auto-rejected non-premium recruiter long-term job ID: {} (waiting {} days)",
                        job.getId(), daysWaiting);

                // Send rejection email notification
                try {
                    emailService.sendJobRejectionNotification(job.getRecruiterProfile().getUser().getEmail(),
                            job.getTitle(), "Not approved within 1 week");
                } catch (Exception e) {
                    log.error("Failed to send rejection email for job ID: {}", job.getId(), e);
                }
            }
        }

        log.info("Auto-process long-term jobs task completed. Approved: {}, Rejected: {}", approvedCount, rejectedCount);
    }

    /**
     * Auto-reject pending job applications when job deadline passes
     * Runs daily at 3:00 AM
     */
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void autoRejectExpiredJobApplications() {
        log.info("Starting auto-reject expired job applications task...");

        LocalDate today = LocalDate.now();

        // Find all CLOSED jobs (past deadline)
        List<JobPosting> closedJobs = jobPostingRepository.findByStatusAndDeadlineBefore(JobStatus.CLOSED, today);

        if (closedJobs.isEmpty()) {
            log.info("No closed long-term jobs found");
            return;
        }

        int rejectedCount = 0;

        for (JobPosting job : closedJobs) {
            // Find all PENDING applications for this job
            List<JobApplication> pendingApps = jobApplicationRepository
                    .findByJobPostingIdAndStatus(job.getId(), JobApplicationStatus.PENDING);

            for (JobApplication app : pendingApps) {
                app.setStatus(JobApplicationStatus.REJECTED);
                app.setRejectionReason("Job deadline has passed");
                app.setProcessedAt(LocalDateTime.now());
                jobApplicationRepository.save(app);
                rejectedCount++;

                // Send rejection email notification
                try {
                    emailService.sendApplicationRejectionNotification(
                            app.getUser().getEmail(),
                            job.getTitle(),
                            "Job deadline has passed"
                    );
                } catch (Exception e) {
                    log.error("Failed to send rejection email for application ID: {}", app.getId(), e);
                }

                log.info("Auto-rejected application ID: {} for job ID: {} (job deadline passed)",
                        app.getId(), job.getId());
            }
        }

        log.info("Auto-reject expired job applications task completed. Rejected {} applications", rejectedCount);
    }

    // ==================== SHORT-TERM JOB SCHEDULERS ====================

    /**
     * Auto-close short-term jobs that are PUBLISHED/APPLIED and past their deadline
     * Runs daily at 1:30 AM
     */
    @Scheduled(cron = "0 0 1 * * ?")
    @Transactional
    public void autoCloseExpiredShortTermJobs() {
        log.info("Starting auto-close expired short-term jobs task...");

        LocalDateTime now = LocalDateTime.now();
        List<ShortTermJob> expiredJobs = shortTermJobRepository.findPublishedJobsWithDeadlinePassed(now);

        if (expiredJobs.isEmpty()) {
            log.info("No expired short-term jobs found");
            return;
        }

        int closedCount = 0;
        for (ShortTermJob job : expiredJobs) {
            job.setStatus(ShortTermJobStatus.CANCELLED);
            shortTermJobRepository.save(job);
            closedCount++;
            log.info("Auto-cancelled short-term job ID: {} (title: {}, deadline: {})",
                    job.getId(), job.getTitle(), job.getDeadline());
        }

        log.info("Auto-close short-term jobs task completed. Cancelled {} jobs", closedCount);
    }

    /**
     * Auto-approve or auto-reject pending short-term jobs based on recruiter subscription
     * - Premium recruiter: auto-approve after 1 day
     * - Normal recruiter: auto-reject after 1 week
     * Runs daily at 2:30 AM
     */
    @Scheduled(cron = "0 30 2 * * ?")
    @Transactional
    public void autoProcessPendingShortTermJobs() {
        log.info("Starting auto-process pending short-term jobs task...");

        LocalDateTime now = LocalDateTime.now();
        List<ShortTermJob> pendingJobs = shortTermJobRepository.findByStatusAndCreatedAtBefore(
                ShortTermJobStatus.PENDING_APPROVAL, now.minusDays(1));

        if (pendingJobs.isEmpty()) {
            log.info("No pending short-term jobs found");
            return;
        }

        int approvedCount = 0;
        int rejectedCount = 0;

        for (ShortTermJob job : pendingJobs) {
            Long recruiterUserId = job.getRecruiterProfile().getUserId();
            LocalDateTime createdAt = job.getCreatedAt();
            long daysWaiting = java.time.Duration.between(createdAt, now).toDays();

            boolean hasPremium = recruiterSubscriptionService.hasActiveRecruiterSubscription(recruiterUserId);

            if (hasPremium && daysWaiting >= PREMIUM_APPROVAL_DAYS) {
                // Auto-approve for premium recruiter after 1 day
                job.setStatus(ShortTermJobStatus.PUBLISHED);
                job.setPublishedAt(now);
                shortTermJobRepository.save(job);
                approvedCount++;
                log.info("Auto-approved premium recruiter short-term job ID: {} (waiting {} days)",
                        job.getId(), daysWaiting);

                // Send approval email notification
                try {
                    emailService.sendJobApprovalNotification(job.getRecruiterProfile().getUser().getEmail(),
                            job.getTitle(), "Short-term job approved");
                } catch (Exception e) {
                    log.error("Failed to send approval email for short-term job ID: {}", job.getId(), e);
                }
            } else if (!hasPremium && daysWaiting >= NORMAL_APPROVAL_DAYS) {
                // Auto-reject for non-premium recruiter after 1 week
                job.setStatus(ShortTermJobStatus.REJECTED);
                shortTermJobRepository.save(job);
                rejectedCount++;
                log.info("Auto-rejected non-premium recruiter short-term job ID: {} (waiting {} days)",
                        job.getId(), daysWaiting);

                // Send rejection email notification
                try {
                    emailService.sendJobRejectionNotification(job.getRecruiterProfile().getUser().getEmail(),
                            job.getTitle(), "Not approved within 1 week");
                } catch (Exception e) {
                    log.error("Failed to send rejection email for short-term job ID: {}", job.getId(), e);
                }
            }
        }

        log.info("Auto-process short-term jobs task completed. Approved: {}, Rejected: {}", approvedCount, rejectedCount);
    }

    /**
     * Auto-reject pending short-term job applications when job is cancelled/expired
     * Runs daily at 3:30 AM
     */
    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void autoRejectExpiredShortTermApplications() {
        log.info("Starting auto-reject expired short-term job applications task...");

        // Find all CANCELLED or EXPIRED short-term jobs
        List<ShortTermJob> cancelledJobs = shortTermJobRepository.findByStatus(ShortTermJobStatus.CANCELLED);

        if (cancelledJobs.isEmpty()) {
            log.info("No cancelled short-term jobs found");
            return;
        }

        int rejectedCount = 0;

        for (ShortTermJob job : cancelledJobs) {
            // Find all PENDING applications for this job
            List<ShortTermJobApplication> pendingApps = shortTermApplicationRepository
                    .findByShortTermJobIdAndStatus(job.getId(), ShortTermApplicationStatus.PENDING);

            for (ShortTermJobApplication app : pendingApps) {
                app.setStatus(ShortTermApplicationStatus.REJECTED);
                shortTermApplicationRepository.save(app);
                rejectedCount++;

                // Send rejection email notification
                try {
                    emailService.sendApplicationRejectionNotification(
                            app.getUser().getEmail(),
                            job.getTitle(),
                            "Job has been cancelled"
                    );
                } catch (Exception e) {
                    log.error("Failed to send rejection email for application ID: {}", app.getId(), e);
                }

                log.info("Auto-rejected short-term application ID: {} for job ID: {} (job cancelled)",
                        app.getId(), job.getId());
            }
        }

        log.info("Auto-reject expired short-term applications task completed. Rejected {} applications", rejectedCount);
    }
}
