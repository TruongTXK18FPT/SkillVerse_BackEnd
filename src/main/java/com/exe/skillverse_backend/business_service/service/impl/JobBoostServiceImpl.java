package com.exe.skillverse_backend.business_service.service.impl;

import com.exe.skillverse_backend.business_service.dto.request.CreateJobBoostRequest;
import com.exe.skillverse_backend.business_service.dto.response.JobBoostAnalyticsResponse;
import com.exe.skillverse_backend.business_service.dto.response.JobBoostResponse;
import com.exe.skillverse_backend.business_service.entity.JobBoost;
import com.exe.skillverse_backend.business_service.entity.JobPosting;
import com.exe.skillverse_backend.business_service.entity.enums.JobBoostStatus;
import com.exe.skillverse_backend.business_service.entity.enums.JobStatus;
import com.exe.skillverse_backend.business_service.repository.JobBoostRepository;
import com.exe.skillverse_backend.business_service.repository.JobPostingRepository;
import com.exe.skillverse_backend.business_service.service.JobBoostService;
import com.exe.skillverse_backend.premium_service.entity.FeatureType;
import com.exe.skillverse_backend.premium_service.service.RecruiterSubscriptionService;
import com.exe.skillverse_backend.premium_service.service.UsageLimitService;
import com.exe.skillverse_backend.shared.exception.BadRequestException;
import com.exe.skillverse_backend.shared.exception.ForbiddenException;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of JobBoostService
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class JobBoostServiceImpl implements JobBoostService {

    private final JobBoostRepository jobBoostRepository;
    private final JobPostingRepository jobPostingRepository;
    private final RecruiterSubscriptionService recruiterSubscriptionService;
    private final UsageLimitService usageLimitService;

    private static final int DEFAULT_BOOST_DAYS = 7;
    private static final int MAX_BOOST_DAYS = 30;

    @Override
    @Transactional
    public JobBoostResponse createBoost(Long recruiterId, CreateJobBoostRequest request) {
        log.info("Creating job boost for job ID: {} by recruiter: {}", request.getJobId(), recruiterId);

        // 1. Validate recruiter has premium subscription
        if (!recruiterSubscriptionService.hasActiveRecruiterSubscription(recruiterId)) {
            throw new ForbiddenException("Bạn cần gói Premium Recruiter để sử dụng tính năng đẩy tin.");
        }

        // 2. Check boost quota
        int availableQuota = getAvailableBoostQuota(recruiterId);
        if (availableQuota <= 0) {
            throw new BadRequestException("Bạn đã hết quota đẩy tin trong tháng này.");
        }

        // 3. Validate job exists and belongs to recruiter
        JobPosting job = jobPostingRepository.findByIdAndRecruiterProfileUserId(request.getJobId(), recruiterId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy tin tuyển dụng hoặc bạn không có quyền."));

        // 4. Validate job is not closed
        if (job.getStatus() == JobStatus.CLOSED) {
            throw new BadRequestException("Không thể đẩy tin đã đóng.");
        }

        // 5. Check if job already has active boost
        if (jobBoostRepository.findActiveBoostByJobPostingId(request.getJobId()).isPresent()) {
            throw new BadRequestException("Tin này đang được đẩy rồi.");
        }

        // 6. Check quota — do NOT record yet (record after boost is fully saved)
        try {
            usageLimitService.checkQuotaOnly(recruiterId, FeatureType.JOB_BOOST_MONTHLY);
        } catch (Exception e) {
            log.warn("Job boost quota exceeded for recruiter {}: {}", recruiterId, e.getMessage());
            throw new BadRequestException("Bạn đã hết quota đẩy tin trong tháng này.");
        }

        // 7. Calculate boost timing
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startedAt;
        LocalDateTime expiresAt;

        if (request.getScheduledStartAt() != null && request.getScheduledStartAt().isAfter(now)) {
            // Scheduled boost
            startedAt = now;
            expiresAt = request.getScheduledStartAt().plusDays(request.getDurationDays());
            // Validate scheduled time is not too far
            if (request.getScheduledStartAt().isAfter(now.plusDays(30))) {
                throw new BadRequestException("Không thể lên lịch đẩy tin quá 30 ngày trước.");
            }
        } else if (request.getExpiresAt() != null) {
            // Custom expiration
            startedAt = now;
            expiresAt = request.getExpiresAt();
            if (expiresAt.isBefore(now) || expiresAt.isAfter(now.plusDays(MAX_BOOST_DAYS))) {
                throw new BadRequestException("Thời hạn đẩy tin không hợp lệ (1-30 ngày).");
            }
        } else {
            // Default: start now, expire after duration days
            startedAt = now;
            expiresAt = now.plusDays(request.getDurationDays());
        }

        // 8. Create boost
        JobBoost boost = JobBoost.builder()
                .jobPosting(job)
                .recruiterId(recruiterId)
                .boostStatus(request.getScheduledStartAt() != null && request.getScheduledStartAt().isAfter(now)
                        ? JobBoostStatus.SCHEDULED
                        : JobBoostStatus.ACTIVE)
                .startedAt(startedAt)
                .expiresAt(expiresAt)
                .scheduledStartAt(request.getScheduledStartAt())
                .createdBy(recruiterId)
                .impressions(0)
                .clicks(0)
                .applications(0)
                .build();

        JobBoost savedBoost = jobBoostRepository.save(boost);
        log.info("Job boost created successfully: ID={}, jobId={}, expiresAt={}",
                savedBoost.getId(), request.getJobId(), expiresAt);

        return mapToResponse(savedBoost);
    }

    @Override
    @Transactional(readOnly = true)
    public JobBoostResponse getBoostByJobId(Long jobId) {
        JobBoost boost = jobBoostRepository.findByJobPostingId(jobId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy thông tin đẩy tin."));

        return mapToResponse(boost);
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobBoostResponse> getBoostsByRecruiter(Long recruiterId) {
        List<JobBoost> boosts = jobBoostRepository.findByRecruiterIdAndBoostStatus(
                recruiterId, JobBoostStatus.ACTIVE);

        return boosts.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public JobBoostResponse cancelBoost(Long recruiterId, Long boostId) {
        JobBoost boost = jobBoostRepository.findById(boostId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy thông tin đẩy tin."));

        // Validate ownership
        if (!boost.getRecruiterId().equals(recruiterId)) {
            throw new ForbiddenException("Bạn không có quyền hủy đẩy tin này.");
        }

        // Only allow cancellation of active or scheduled boosts
        if (boost.getBoostStatus() == JobBoostStatus.CANCELLED) {
            throw new BadRequestException("Đẩy tin này đã bị hủy trước đó.");
        }

        boost.setBoostStatus(JobBoostStatus.CANCELLED);
        JobBoost savedBoost = jobBoostRepository.save(boost);

        log.info("Boost cancelled: ID={}, recruiterId={}", boostId, recruiterId);
        return mapToResponse(savedBoost);
    }

    @Override
    @Transactional
    public JobBoostResponse extendBoost(Long recruiterId, Long boostId, int additionalDays) {
        if (additionalDays < 1 || additionalDays > 30) {
            throw new BadRequestException("Số ngày mở rộng phải từ 1-30 ngày.");
        }

        JobBoost boost = jobBoostRepository.findById(boostId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy thông tin đẩy tin."));

        // Validate ownership
        if (!boost.getRecruiterId().equals(recruiterId)) {
            throw new ForbiddenException("Bạn không có quyền mở rộng đẩy tin này.");
        }

        // Only allow extension of active boosts
        if (boost.getBoostStatus() != JobBoostStatus.ACTIVE) {
            throw new BadRequestException("Chỉ có thể mở rộng đẩy tin đang hoạt động.");
        }

        // Check if boost has expired
        if (boost.isExpired()) {
            throw new BadRequestException("Đẩy tin đã hết hạn, không thể mở rộng.");
        }

        // Extend expiration
        LocalDateTime newExpiresAt = boost.getExpiresAt().plusDays(additionalDays);

        // Validate max duration
        long totalDays = ChronoUnit.DAYS.between(boost.getStartedAt(), newExpiresAt);
        if (totalDays > MAX_BOOST_DAYS) {
            throw new BadRequestException("Tổng thời gian đẩy tin không được quá 30 ngày.");
        }

        boost.setExpiresAt(newExpiresAt);
        JobBoost savedBoost = jobBoostRepository.save(boost);

        log.info("Boost extended: ID={}, newExpiresAt={}", boostId, newExpiresAt);
        return mapToResponse(savedBoost);
    }

    @Override
    @Transactional(readOnly = true)
    public JobBoostAnalyticsResponse getBoostAnalytics(Long recruiterId, Long boostId) {
        JobBoost boost = jobBoostRepository.findById(boostId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy thông tin đẩy tin."));

        // Validate ownership
        if (!boost.getRecruiterId().equals(recruiterId)) {
            throw new ForbiddenException("Bạn không có quyền xem thống kê này.");
        }

        // Calculate metrics
        int impressions = boost.getImpressions() != null ? boost.getImpressions() : 0;
        int clicks = boost.getClicks() != null ? boost.getClicks() : 0;
        int applications = boost.getApplications() != null ? boost.getApplications() : 0;

        double ctr = impressions > 0 ? (double) clicks / impressions : 0.0;
        double acr = impressions > 0 ? (double) applications / impressions : 0.0;

        long durationMinutes = ChronoUnit.MINUTES.between(boost.getStartedAt(),
                boost.getExpiresAt().isBefore(LocalDateTime.now()) ? boost.getExpiresAt() : LocalDateTime.now());

        return JobBoostAnalyticsResponse.builder()
                .boostId(boost.getId())
                .jobId(boost.getJobPosting().getId())
                .jobTitle(boost.getJobPosting().getTitle())
                .totalImpressions(impressions)
                .totalClicks(clicks)
                .totalApplications(applications)
                .clickThroughRate(Math.round(ctr * 10000.0) / 100.0)
                .applicationConversionRate(Math.round(acr * 10000.0) / 100.0)
                .boostStartedAt(boost.getStartedAt())
                .boostExpiresAt(boost.getExpiresAt())
                .totalBoostDurationMinutes(durationMinutes)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public int getAvailableBoostQuota(Long recruiterId) {
        if (!recruiterSubscriptionService.hasActiveRecruiterSubscription(recruiterId)) {
            return 0;
        }

        try {
            var usage = usageLimitService.getUserUsage(recruiterId, FeatureType.JOB_BOOST_MONTHLY);
            if (usage.getIsUnlimited() != null && usage.getIsUnlimited()) {
                return Integer.MAX_VALUE; // Unlimited
            }
            int limit = usage.getLimit() != null ? usage.getLimit() : 0;
            int used = usage.getCurrentUsage() != null ? usage.getCurrentUsage() : 0;
            return Math.max(0, limit - used);
        } catch (Exception e) {
            log.warn("Failed to get boost quota for recruiter {}: {}", recruiterId, e.getMessage());
            return 0;
        }
    }

    @Override
    @Transactional
    public void recordImpression(Long jobId, Long userId, Integer position) {
        jobBoostRepository.findActiveBoostByJobPostingId(jobId).ifPresent(boost -> {
            boost.incrementImpressions();
            jobBoostRepository.save(boost);
            log.debug("Recorded impression for boost ID: {}, position: {}", boost.getId(), position);
        });
    }

    @Override
    @Transactional
    public void recordClick(Long jobId, Long userId) {
        jobBoostRepository.findActiveBoostByJobPostingId(jobId).ifPresent(boost -> {
            boost.incrementClicks();
            jobBoostRepository.save(boost);
            log.debug("Recorded click for boost ID: {}", boost.getId());
        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> getActiveBoostedJobIds() {
        return jobBoostRepository.findAllActiveBoostedJobIds();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasActiveBoost(Long jobId) {
        return jobBoostRepository.hasActiveBoost(jobId);
    }

    @Override
    @Transactional
    public void processExpiredBoosts() {
        log.info("Processing expired job boosts...");

        List<JobBoost> expiredBoosts = jobBoostRepository.findBoostsExpiringBefore(LocalDateTime.now());

        for (JobBoost boost : expiredBoosts) {
            if (boost.getBoostStatus() == JobBoostStatus.ACTIVE) {
                boost.setBoostStatus(JobBoostStatus.EXPIRED);
                jobBoostRepository.save(boost);
                log.info("Boost expired: ID={}, jobId={}", boost.getId(), boost.getJobPosting().getId());
            }
        }

        log.info("Processed {} expired boosts", expiredBoosts.size());
    }

    @Override
    @Transactional
    public void activateScheduledBoosts() {
        log.info("Activating scheduled job boosts...");

        List<JobBoost> readyBoosts = jobBoostRepository.findScheduledBoostsReadyToActivate(LocalDateTime.now());

        for (JobBoost boost : readyBoosts) {
            if (boost.getScheduledStartAt() != null && !boost.getScheduledStartAt().isAfter(LocalDateTime.now())) {
                boost.setBoostStatus(JobBoostStatus.ACTIVE);
                boost.setStartedAt(LocalDateTime.now());
                jobBoostRepository.save(boost);
                log.info("Scheduled boost activated: ID={}, jobId={}", boost.getId(), boost.getJobPosting().getId());
            }
        }

        log.info("Activated {} scheduled boosts", readyBoosts.size());
    }

    // ==================== Helper Methods ====================

    private JobBoostResponse mapToResponse(JobBoost boost) {
        int impressions = boost.getImpressions() != null ? boost.getImpressions() : 0;
        int clicks = boost.getClicks() != null ? boost.getClicks() : 0;
        int applications = boost.getApplications() != null ? boost.getApplications() : 0;

        double ctr = impressions > 0 ? (double) clicks / impressions : 0.0;
        double acr = impressions > 0 ? (double) applications / impressions : 0.0;

        return JobBoostResponse.builder()
                .id(boost.getId())
                .jobId(boost.getJobPosting().getId())
                .jobTitle(boost.getJobPosting().getTitle())
                .companyName(boost.getJobPosting().getRecruiterProfile().getCompanyName())
                .recruiterId(boost.getRecruiterId())
                .boostStatus(boost.getBoostStatus())
                .startedAt(boost.getStartedAt())
                .expiresAt(boost.getExpiresAt())
                .scheduledStartAt(boost.getScheduledStartAt())
                .impressions(impressions)
                .clicks(clicks)
                .applications(applications)
                .createdAt(boost.getCreatedAt())
                .updatedAt(boost.getUpdatedAt())
                .isActive(boost.isActive())
                .remainingMinutes(boost.getRemainingMinutes())
                .clickThroughRate(Math.round(ctr * 100.0) / 100.0)
                .applicationConversionRate(Math.round(acr * 100.0) / 100.0)
                .build();
    }
}
