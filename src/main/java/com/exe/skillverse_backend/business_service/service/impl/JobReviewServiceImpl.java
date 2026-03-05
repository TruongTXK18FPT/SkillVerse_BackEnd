package com.exe.skillverse_backend.business_service.service.impl;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.business_service.dto.request.CreateJobReviewRequest;
import com.exe.skillverse_backend.business_service.dto.response.JobReviewResponse;
import com.exe.skillverse_backend.business_service.dto.response.UserRatingSummary;
import com.exe.skillverse_backend.business_service.entity.JobReview;
import com.exe.skillverse_backend.business_service.entity.ShortTermJob;
import com.exe.skillverse_backend.business_service.entity.ShortTermJobApplication;
import com.exe.skillverse_backend.business_service.entity.enums.ShortTermApplicationStatus;
import com.exe.skillverse_backend.business_service.entity.enums.ShortTermJobStatus;
import com.exe.skillverse_backend.business_service.repository.JobReviewRepository;
import com.exe.skillverse_backend.business_service.repository.ShortTermJobApplicationRepository;
import com.exe.skillverse_backend.business_service.service.JobReviewService;
import com.exe.skillverse_backend.shared.exception.BadRequestException;
import com.exe.skillverse_backend.shared.exception.ForbiddenException;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class JobReviewServiceImpl implements JobReviewService {

    private final JobReviewRepository reviewRepository;
    private final ShortTermJobApplicationRepository applicationRepository;
    private final UserRepository userRepository;

    @Override
    public JobReviewResponse createReview(Long userId, CreateJobReviewRequest request) {
        log.info("User ID: {} creating review for application ID: {}", userId, request.getApplicationId());

        User reviewer = getUserById(userId);
        ShortTermJobApplication application = getApplicationById(request.getApplicationId());

        // Validate review can be created
        validateCanCreateReview(userId, application);

        // Determine review type and reviewee
        JobReview.ReviewType reviewType;
        User reviewee;

        ShortTermJob job = application.getShortTermJob();
        boolean isRecruiter = job.getRecruiterProfile().getUser().getId().equals(userId);

        if (isRecruiter) {
            reviewType = JobReview.ReviewType.RECRUITER_TO_CANDIDATE;
            reviewee = application.getUser();
        } else if (application.getUser().getId().equals(userId)) {
            reviewType = JobReview.ReviewType.CANDIDATE_TO_RECRUITER;
            reviewee = job.getRecruiterProfile().getUser();
        } else {
            throw new ForbiddenException("You are not authorized to review this application");
        }

        // Check if review already exists
        if (reviewRepository.existsByApplicationIdAndReviewerId(request.getApplicationId(), userId)) {
            throw new BadRequestException("You have already reviewed this application");
        }

        JobReview review = JobReview.builder()
                .application(application)
                .reviewer(reviewer)
                .reviewee(reviewee)
                .reviewType(reviewType)
                .rating(request.getRating())
                .comment(request.getComment())
                .strengths(request.getStrengths())
                .improvements(request.getImprovements())
                .recommendations(request.getRecommendations())
                .communicationRating(request.getCommunicationRating())
                .qualityRating(request.getQualityRating())
                .timelinessRating(request.getTimelinessRating())
                .professionalismRating(request.getProfessionalismRating())
                .isPublic(request.getIsPublic() != null ? request.getIsPublic() : true)
                .build();

        review = reviewRepository.save(review);
        log.info("Review created with ID: {}", review.getId());

        return mapToResponse(review);
    }

    @Override
    @Transactional(readOnly = true)
    public JobReviewResponse getReviewById(Long reviewId) {
        return mapToResponse(getReview(reviewId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobReviewResponse> getReviewsForApplication(Long applicationId) {
        return reviewRepository.findByApplicationId(applicationId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobReviewResponse> getReviewsWrittenByUser(Long userId) {
        return reviewRepository.findByReviewerIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<JobReviewResponse> getReviewsWrittenByUserPaged(Long userId, Pageable pageable) {
        return reviewRepository.findByReviewerId(userId, pageable)
                .map(this::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobReviewResponse> getPublicReviewsForUser(Long userId) {
        return reviewRepository.findPublicReviewsForUser(userId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<JobReviewResponse> getPublicReviewsForUserPaged(Long userId, Pageable pageable) {
        return reviewRepository.findPublicReviewsForUser(userId, pageable)
                .map(this::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public UserRatingSummary getUserRatingSummary(Long userId) {
        User user = getUserById(userId);

        BigDecimal averageRating = reviewRepository.getAverageRatingForUser(userId);
        long totalReviews = reviewRepository.countPublicReviewsForUser(userId);
        long totalCompletedJobs = applicationRepository.countCompletedByUser(userId);

        // Get rating breakdown
        List<Object[]> breakdown = reviewRepository.getRatingBreakdownForUser(userId);
        int fiveStars = 0, fourStars = 0, threeStars = 0, twoStars = 0, oneStars = 0;
        for (Object[] row : breakdown) {
            int rating = (Integer) row[0];
            long count = (Long) row[1];
            switch (rating) {
                case 5: fiveStars = (int) count; break;
                case 4: fourStars = (int) count; break;
                case 3: threeStars = (int) count; break;
                case 2: twoStars = (int) count; break;
                case 1: oneStars = (int) count; break;
            }
        }

        // Get specific ratings — use Number cast to safely convert AVG results
        // JPA may return Object[] directly or wrap it in another Object[] depending on provider
        Object[] rawResult = reviewRepository.getAverageSpecificRatingsForUser(userId);
        Object[] specificRatings;
        if (rawResult != null && rawResult.length == 1 && rawResult[0] instanceof Object[]) {
            specificRatings = (Object[]) rawResult[0];
        } else {
            specificRatings = rawResult;
        }
        BigDecimal avgCommunication = (specificRatings != null && specificRatings.length > 0 && specificRatings[0] != null) ? 
                BigDecimal.valueOf(((Number) specificRatings[0]).doubleValue()).setScale(2, RoundingMode.HALF_UP) : null;
        BigDecimal avgQuality = (specificRatings != null && specificRatings.length > 1 && specificRatings[1] != null) ? 
                BigDecimal.valueOf(((Number) specificRatings[1]).doubleValue()).setScale(2, RoundingMode.HALF_UP) : null;
        BigDecimal avgTimeliness = (specificRatings != null && specificRatings.length > 2 && specificRatings[2] != null) ? 
                BigDecimal.valueOf(((Number) specificRatings[2]).doubleValue()).setScale(2, RoundingMode.HALF_UP) : null;
        BigDecimal avgProfessionalism = (specificRatings != null && specificRatings.length > 3 && specificRatings[3] != null) ? 
                BigDecimal.valueOf(((Number) specificRatings[3]).doubleValue()).setScale(2, RoundingMode.HALF_UP) : null;

        // Calculate completion rate
        long totalApplications = applicationRepository.countByUserId(userId);
        BigDecimal completionRate = totalApplications > 0 ?
                BigDecimal.valueOf(totalCompletedJobs)
                        .divide(BigDecimal.valueOf(totalApplications), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)) : BigDecimal.ZERO;

        return UserRatingSummary.builder()
                .userId(userId)
                .userName(user.getFullName())
                .averageRating(averageRating != null ? 
                        averageRating.setScale(2, RoundingMode.HALF_UP) : null)
                .totalReviews((int) totalReviews)
                .totalCompletedJobs((int) totalCompletedJobs)
                .fiveStarCount(fiveStars)
                .fourStarCount(fourStars)
                .threeStarCount(threeStars)
                .twoStarCount(twoStars)
                .oneStarCount(oneStars)
                .averageCommunicationRating(avgCommunication)
                .averageQualityRating(avgQuality)
                .averageTimelinessRating(avgTimeliness)
                .averageProfessionalismRating(avgProfessionalism)
                .completionRate(completionRate.setScale(2, RoundingMode.HALF_UP))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canWriteReview(Long userId, Long applicationId) {
        try {
            ShortTermJobApplication application = getApplicationById(applicationId);
            validateCanCreateReview(userId, application);
            return !reviewRepository.existsByApplicationIdAndReviewerId(applicationId, userId);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public JobReviewResponse updateReviewVisibility(Long userId, Long reviewId, Boolean isPublic) {
        JobReview review = getReview(reviewId);

        if (!review.getReviewer().getId().equals(userId)) {
            throw new ForbiddenException("You can only update visibility of your own reviews");
        }

        review.setIsPublic(isPublic);
        review = reviewRepository.save(review);

        return mapToResponse(review);
    }

    // ==================== VALIDATION HELPERS ====================

    private void validateCanCreateReview(Long userId, ShortTermJobApplication application) {
        // Check if application is completed and paid
        if (application.getStatus() != ShortTermApplicationStatus.COMPLETED) {
            throw new BadRequestException("Can only review completed jobs");
        }

        ShortTermJob job = application.getShortTermJob();
        if (job.getStatus() != ShortTermJobStatus.PAID) {
            throw new BadRequestException("Can only review after job is paid");
        }

        // Check if user is involved in this application
        boolean isRecruiter = job.getRecruiterProfile().getUser().getId().equals(userId);
        boolean isCandidate = application.getUser().getId().equals(userId);

        if (!isRecruiter && !isCandidate) {
            throw new ForbiddenException("You are not involved in this job");
        }
    }

    // ==================== ENTITY HELPERS ====================

    private JobReview getReview(Long reviewId) {
        return reviewRepository.findById(reviewId)
                .orElseThrow(() -> new NotFoundException("Review not found with ID: " + reviewId));
    }

    private ShortTermJobApplication getApplicationById(Long applicationId) {
        return applicationRepository.findById(applicationId)
                .orElseThrow(() -> new NotFoundException("Application not found with ID: " + applicationId));
    }

    private User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found with ID: " + userId));
    }

    // ==================== MAPPING HELPERS ====================

    private JobReviewResponse mapToResponse(JobReview review) {
        ShortTermJob job = review.getApplication().getShortTermJob();

        return JobReviewResponse.builder()
                .id(review.getId())
                .applicationId(review.getApplication().getId())
                .jobTitle(job.getTitle())
                .reviewerId(review.getReviewer().getId())
                .reviewerName(review.getReviewer().getFullName())
                .revieweeId(review.getReviewee().getId())
                .revieweeName(review.getReviewee().getFullName())
                .reviewType(review.getReviewType())
                .rating(review.getRating())
                .comment(review.getComment())
                .strengths(review.getStrengths())
                .improvements(review.getImprovements())
                .recommendations(review.getRecommendations())
                .communicationRating(review.getCommunicationRating())
                .qualityRating(review.getQualityRating())
                .timelinessRating(review.getTimelinessRating())
                .professionalismRating(review.getProfessionalismRating())
                .isPublic(review.getIsPublic())
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .build();
    }
}
