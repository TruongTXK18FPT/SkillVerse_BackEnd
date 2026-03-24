package com.exe.skillverse_backend.business_service.service.impl;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.business_service.entity.TrustScore;
import com.exe.skillverse_backend.business_service.repository.DisputeRepository;
import com.exe.skillverse_backend.business_service.repository.JobReviewRepository;
import com.exe.skillverse_backend.business_service.repository.ShortTermJobApplicationRepository;
import com.exe.skillverse_backend.business_service.repository.ShortTermJobRepository;
import com.exe.skillverse_backend.business_service.repository.TrustScoreRepository;
import com.exe.skillverse_backend.business_service.service.TrustScoreService;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class TrustScoreServiceImpl implements TrustScoreService {

    private final TrustScoreRepository trustScoreRepository;
    private final UserRepository userRepository;
    private final ShortTermJobRepository shortTermJobRepository;
    private final ShortTermJobApplicationRepository applicationRepository;
    private final JobReviewRepository reviewRepository;
    private final DisputeRepository disputeRepository;

    @Override
    public TrustScore calculateScore(Long userId) {
        log.info("Calculating trust score for user ID: {}", userId);
        return recalculateScore(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public TrustScore getScore(Long userId) {
        return trustScoreRepository.findByUserId(userId).orElse(null);
    }

    @Override
    public TrustScore recalculateScore(Long userId) {
        log.info("Recalculating trust score for user ID: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found with ID: " + userId));

        // Get or create trust score record
        TrustScore trustScore = trustScoreRepository.findByUserId(userId).orElseGet(() -> {
            TrustScore newScore = TrustScore.builder()
                    .user(user)
                    .build();
            return trustScoreRepository.save(newScore);
        });

        // Count jobs as recruiter
        long totalRecruiterJobs = shortTermJobRepository.countByRecruiterProfileUserId(userId);
        long completedRecruiterJobs = shortTermJobRepository.countCompletedByRecruiterProfile(userId);

        // Count jobs as worker
        long totalWorkerJobs = applicationRepository.countByUserId(userId);
        long completedWorkerJobs = applicationRepository.countCompletedByUser(userId);

        int totalJobs = (int) (totalRecruiterJobs + totalWorkerJobs);
        int completedJobs = (int) (completedRecruiterJobs + completedWorkerJobs);

        trustScore.setTotalJobs(totalJobs);
        trustScore.setCompletedJobs(completedJobs);

        // Completion rate
        BigDecimal completionRate = BigDecimal.ZERO;
        if (totalJobs > 0) {
            completionRate = BigDecimal.valueOf(completedJobs)
                    .divide(BigDecimal.valueOf(totalJobs), 4, RoundingMode.HALF_UP);
        }
        trustScore.setCompletionRate(completionRate);

        // Average rating from reviews
        BigDecimal avgRating = reviewRepository.getAverageRatingForUser(userId);
        if (avgRating == null) {
            avgRating = BigDecimal.ZERO;
        }
        trustScore.setAvgRating(avgRating);

        // Total reviews count
        long reviewCount = reviewRepository.countByUserId(userId);
        trustScore.setTotalReviews((int) reviewCount);

        // Dispute rate
        long disputedAsInitiator = disputeRepository.findByInitiatorId(userId).size();
        long disputedAsRespondent = disputeRepository.findByRespondentId(userId).size();
        int disputedJobs = (int) (disputedAsInitiator + disputedAsRespondent);
        trustScore.setDisputedJobs(disputedJobs);

        BigDecimal disputeRate = BigDecimal.ZERO;
        if (totalJobs > 0) {
            disputeRate = BigDecimal.valueOf(disputedJobs)
                    .divide(BigDecimal.valueOf(totalJobs), 4, RoundingMode.HALF_UP);
        }
        trustScore.setDisputeRate(disputeRate);

        // Account age in days
        if (user.getCreatedAt() != null) {
            int accountAgeDays = (int) ChronoUnit.DAYS.between(user.getCreatedAt(), LocalDateTime.now());
            trustScore.setAccountAgeDays(accountAgeDays);
        }

        // Response time (placeholder - would need message/chat data for real calculation)
        trustScore.setResponseTimeHours(BigDecimal.ZERO);

        // Calculate total score using the entity method
        trustScore.recalculate(
                trustScore.getCompletionRate(),
                trustScore.getAvgRating(),
                trustScore.getDisputeRate(),
                trustScore.getResponseTimeHours(),
                trustScore.getAccountAgeDays()
        );

        trustScore = trustScoreRepository.save(trustScore);
        log.info("Trust score calculated for user {}: {} (Tier: {})",
                userId, trustScore.getTotalScore(), trustScore.getTrustTier());

        return trustScore;
    }

    @Override
    public void triggerRecalculation(Long userId) {
        recalculateScore(userId);
    }

    @Override
    public void triggerRecalculationOnJobComplete(Long recruiterId, Long workerId) {
        if (recruiterId != null) {
            recalculateScore(recruiterId);
        }
        if (workerId != null) {
            recalculateScore(workerId);
        }
    }

    @Override
    public void triggerRecalculationOnDispute(Long userId) {
        recalculateScore(userId);
    }
}
