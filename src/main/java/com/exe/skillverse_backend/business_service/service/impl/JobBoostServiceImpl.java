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

@Service
@Slf4j
@RequiredArgsConstructor
public class JobBoostServiceImpl implements JobBoostService {

    private static final int MIN_BOOST_DAYS = 7;
    private static final int MAX_BOOST_DAYS = 30;

    private final JobBoostRepository jobBoostRepository;
    private final JobPostingRepository jobPostingRepository;
    private final RecruiterSubscriptionService recruiterSubscriptionService;
    private final UsageLimitService usageLimitService;

    @Override
    @Transactional
    public JobBoostResponse createBoost(Long recruiterId, CreateJobBoostRequest request) {
        log.info("Creating job boost for job {} by recruiter {}", request.getJobId(), recruiterId);

        validateBoostDuration(request.getDurationDays());

        if (!recruiterSubscriptionService.hasActiveRecruiterSubscription(recruiterId)) {
            throw new ForbiddenException("Bạn cần gói Premium Recruiter để sử dụng tính năng boost job.");
        }

        if (getAvailableBoostQuota(recruiterId) <= 0) {
            throw new BadRequestException("Bạn đã dùng hết quota boost job trong kỳ hiện tại.");
        }

        JobPosting job = jobPostingRepository.findByIdAndRecruiterProfileUserId(request.getJobId(), recruiterId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy job hoặc bạn không có quyền thao tác."));

        if (job.getStatus() == JobStatus.CLOSED) {
            throw new BadRequestException("Không thể boost job đã đóng.");
        }

        if (jobBoostRepository.findByJobPostingId(request.getJobId()).isPresent()) {
            throw new BadRequestException("Mỗi job chỉ được boost một lần. Job này đã dùng lượt boost trước đó.");
        }

        try {
            usageLimitService.checkQuotaOnly(recruiterId, FeatureType.JOB_BOOST_MONTHLY);
        } catch (Exception exception) {
            log.warn("Recruiter {} exceeded job boost quota: {}", recruiterId, exception.getMessage());
            throw new BadRequestException("Bạn đã dùng hết quota boost job trong kỳ hiện tại.");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startedAt = now;
        LocalDateTime expiresAt;
        JobBoostStatus status = JobBoostStatus.ACTIVE;

        if (request.getScheduledStartAt() != null && request.getScheduledStartAt().isAfter(now)) {
            if (request.getScheduledStartAt().isAfter(now.plusDays(MAX_BOOST_DAYS))) {
                throw new BadRequestException("Không thể lên lịch boost quá 30 ngày kể từ hiện tại.");
            }
            status = JobBoostStatus.SCHEDULED;
            expiresAt = request.getScheduledStartAt().plusDays(request.getDurationDays());
        } else if (request.getExpiresAt() != null) {
            expiresAt = request.getExpiresAt();
            long requestedDays = ChronoUnit.DAYS.between(now, expiresAt);
            if (expiresAt.isBefore(now) || requestedDays < MIN_BOOST_DAYS || requestedDays > MAX_BOOST_DAYS) {
                throw new BadRequestException("Thời lượng boost phải nằm trong khoảng 7 đến 30 ngày.");
            }
        } else {
            expiresAt = now.plusDays(request.getDurationDays());
        }

        JobBoost boost = JobBoost.builder()
                .jobPosting(job)
                .recruiterId(recruiterId)
                .boostStatus(status)
                .startedAt(startedAt)
                .expiresAt(expiresAt)
                .scheduledStartAt(request.getScheduledStartAt())
                .createdBy(recruiterId)
                .impressions(0)
                .clicks(0)
                .applications(0)
                .build();

        JobBoost savedBoost = jobBoostRepository.saveAndFlush(boost);
        usageLimitService.checkAndRecordUsage(recruiterId, FeatureType.JOB_BOOST_MONTHLY);

        log.info("Created job boost {} for job {}. Expires at {}", savedBoost.getId(), request.getJobId(), expiresAt);
        return mapToResponse(savedBoost);
    }

    @Override
    @Transactional(readOnly = true)
    public JobBoostResponse getBoostByJobId(Long jobId) {
        JobBoost boost = jobBoostRepository.findByJobPostingId(jobId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy thông tin boost cho job này."));
        return mapToResponse(boost);
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobBoostResponse> getBoostsByRecruiter(Long recruiterId) {
        return jobBoostRepository.findByRecruiterIdAndBoostStatus(recruiterId, JobBoostStatus.ACTIVE)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public JobBoostResponse cancelBoost(Long recruiterId, Long boostId) {
        JobBoost boost = jobBoostRepository.findById(boostId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy thông tin boost."));

        if (!boost.getRecruiterId().equals(recruiterId)) {
            throw new ForbiddenException("Bạn không có quyền hủy boost này.");
        }

        if (boost.getBoostStatus() == JobBoostStatus.CANCELLED) {
            throw new BadRequestException("Boost này đã bị hủy trước đó.");
        }

        if (boost.getBoostStatus() != JobBoostStatus.ACTIVE && boost.getBoostStatus() != JobBoostStatus.SCHEDULED) {
            throw new BadRequestException("Chỉ có thể hủy boost đang hoạt động hoặc đang chờ kích hoạt.");
        }

        boost.setBoostStatus(JobBoostStatus.CANCELLED);
        JobBoost savedBoost = jobBoostRepository.save(boost);
        log.info("Cancelled boost {} for recruiter {}", boostId, recruiterId);
        return mapToResponse(savedBoost);
    }

    @Override
    @Transactional
    public JobBoostResponse extendBoost(Long recruiterId, Long boostId, int additionalDays) {
        throw new BadRequestException("Mỗi job chỉ được boost một lần. Không hỗ trợ gia hạn boost.");
    }

    @Override
    @Transactional(readOnly = true)
    public JobBoostAnalyticsResponse getBoostAnalytics(Long recruiterId, Long boostId) {
        JobBoost boost = jobBoostRepository.findById(boostId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy thông tin boost."));

        if (!boost.getRecruiterId().equals(recruiterId)) {
            throw new ForbiddenException("Bạn không có quyền xem thống kê boost này.");
        }

        int impressions = boost.getImpressions() != null ? boost.getImpressions() : 0;
        int clicks = boost.getClicks() != null ? boost.getClicks() : 0;
        int applications = boost.getApplications() != null ? boost.getApplications() : 0;

        double ctr = impressions > 0 ? (double) clicks / impressions : 0.0;
        double conversionRate = impressions > 0 ? (double) applications / impressions : 0.0;
        long durationMinutes = ChronoUnit.MINUTES.between(
                boost.getStartedAt(),
                boost.getExpiresAt().isBefore(LocalDateTime.now()) ? boost.getExpiresAt() : LocalDateTime.now()
        );

        return JobBoostAnalyticsResponse.builder()
                .boostId(boost.getId())
                .jobId(boost.getJobPosting().getId())
                .jobTitle(boost.getJobPosting().getTitle())
                .totalImpressions(impressions)
                .totalClicks(clicks)
                .totalApplications(applications)
                .clickThroughRate(Math.round(ctr * 10000.0) / 100.0)
                .applicationConversionRate(Math.round(conversionRate * 10000.0) / 100.0)
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
            if (Boolean.TRUE.equals(usage.getIsUnlimited())) {
                return Integer.MAX_VALUE;
            }
            int limit = usage.getLimit() != null ? usage.getLimit() : 0;
            int used = usage.getCurrentUsage() != null ? usage.getCurrentUsage() : 0;
            return Math.max(0, limit - used);
        } catch (Exception exception) {
            log.warn("Failed to resolve boost quota for recruiter {}: {}", recruiterId, exception.getMessage());
            return 0;
        }
    }

    @Override
    @Transactional
    public void recordImpression(Long jobId, Long userId, Integer position) {
        jobBoostRepository.findActiveBoostByJobPostingId(jobId).ifPresent(boost -> {
            boost.incrementImpressions();
            jobBoostRepository.save(boost);
            log.debug("Recorded impression for boost {} at position {}", boost.getId(), position);
        });
    }

    @Override
    @Transactional
    public void recordClick(Long jobId, Long userId) {
        jobBoostRepository.findActiveBoostByJobPostingId(jobId).ifPresent(boost -> {
            boost.incrementClicks();
            jobBoostRepository.save(boost);
            log.debug("Recorded click for boost {}", boost.getId());
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
        log.info("Processing expired boosts");
        List<JobBoost> expiredBoosts = jobBoostRepository.findBoostsExpiringBefore(LocalDateTime.now());
        for (JobBoost boost : expiredBoosts) {
            if (boost.getBoostStatus() == JobBoostStatus.ACTIVE) {
                boost.setBoostStatus(JobBoostStatus.EXPIRED);
                jobBoostRepository.save(boost);
            }
        }
        log.info("Processed {} expired boosts", expiredBoosts.size());
    }

    @Override
    @Transactional
    public void activateScheduledBoosts() {
        log.info("Activating scheduled boosts");
        List<JobBoost> readyBoosts = jobBoostRepository.findScheduledBoostsReadyToActivate(LocalDateTime.now());
        for (JobBoost boost : readyBoosts) {
            if (boost.getScheduledStartAt() != null && !boost.getScheduledStartAt().isAfter(LocalDateTime.now())) {
                boost.setBoostStatus(JobBoostStatus.ACTIVE);
                boost.setStartedAt(LocalDateTime.now());
                jobBoostRepository.save(boost);
            }
        }
        log.info("Activated {} scheduled boosts", readyBoosts.size());
    }

    private void validateBoostDuration(Integer durationDays) {
        if (durationDays == null || durationDays < MIN_BOOST_DAYS || durationDays > MAX_BOOST_DAYS) {
            throw new BadRequestException("Thời lượng boost phải nằm trong khoảng 7 đến 30 ngày.");
        }
    }

    private JobBoostResponse mapToResponse(JobBoost boost) {
        int impressions = boost.getImpressions() != null ? boost.getImpressions() : 0;
        int clicks = boost.getClicks() != null ? boost.getClicks() : 0;
        int applications = boost.getApplications() != null ? boost.getApplications() : 0;

        double ctr = impressions > 0 ? (double) clicks / impressions : 0.0;
        double conversionRate = impressions > 0 ? (double) applications / impressions : 0.0;

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
                .applicationConversionRate(Math.round(conversionRate * 100.0) / 100.0)
                .build();
    }
}
