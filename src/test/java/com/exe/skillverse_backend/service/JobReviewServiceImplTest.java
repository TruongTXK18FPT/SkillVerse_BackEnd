package com.exe.skillverse_backend.service;

import com.exe.skillverse_backend.business_service.dto.request.CreateJobReviewRequest;
import com.exe.skillverse_backend.business_service.dto.response.JobReviewResponse;
import com.exe.skillverse_backend.business_service.dto.response.UserRatingSummary;
import com.exe.skillverse_backend.business_service.entity.*;
import com.exe.skillverse_backend.business_service.entity.enums.*;
import com.exe.skillverse_backend.business_service.repository.*;
import com.exe.skillverse_backend.business_service.service.impl.JobReviewServiceImpl;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.shared.exception.BadRequestException;
import com.exe.skillverse_backend.shared.exception.ForbiddenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for JobReviewServiceImpl validation rules
 *
 * Test categories:
 * 1. Review Creation Validation
 * 2. Review Eligibility Validation
 * 3. Rating Summary Calculation
 */
@ExtendWith(MockitoExtension.class)
class JobReviewServiceImplTest {

    @Mock
    private JobReviewRepository reviewRepository;

    @Mock
    private ShortTermJobApplicationRepository applicationRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private JobReviewServiceImpl jobReviewService;

    private User mockReviewer;
    private User mockReviewee;
    private ShortTermJob mockJob;
    private ShortTermJobApplication mockApplication;
    private RecruiterProfile mockRecruiterProfile;

    @BeforeEach
    void setUp() {
        // Setup mock reviewer (recruiter)
        mockReviewer = new User();
        mockReviewer.setId(1L);
        mockReviewer.setEmail("reviewer@test.com");
        mockReviewer.setFirstName("Test");
        mockReviewer.setLastName("Reviewer");

        // Setup mock reviewee (applicant)
        mockReviewee = new User();
        mockReviewee.setId(2L);
        mockReviewee.setEmail("reviewee@test.com");
        mockReviewee.setFirstName("Test");
        mockReviewee.setLastName("Reviewee");

        // Setup recruiter profile
        mockRecruiterProfile = new RecruiterProfile();
        mockRecruiterProfile.setUserId(mockReviewer.getId());
        mockRecruiterProfile.setUser(mockReviewer);

        // Setup mock job
        mockJob = new ShortTermJob();
        mockJob.setId(1L);
        mockJob.setTitle("Test Job");
        mockJob.setStatus(ShortTermJobStatus.PAID);
        mockJob.setRecruiterProfile(mockRecruiterProfile);

        // Setup mock application
        mockApplication = new ShortTermJobApplication();
        mockApplication.setId(1L);
        mockApplication.setShortTermJob(mockJob);
        mockApplication.setUser(mockReviewee);
        mockApplication.setStatus(ShortTermApplicationStatus.COMPLETED);
    }

    // ==================== REVIEW CREATION VALIDATION TESTS ====================

    @Nested
    @DisplayName("Review Creation Validation Tests")
    class ReviewCreationValidationTests {

        @Test
        @DisplayName("Should create review successfully with valid data")
        void shouldCreateReviewSuccessfully() {
            CreateJobReviewRequest request = createValidReviewRequest();

            when(userRepository.findById(mockReviewer.getId())).thenReturn(Optional.of(mockReviewer));
            when(applicationRepository.findById(request.getApplicationId())).thenReturn(Optional.of(mockApplication));
            when(reviewRepository.existsByApplicationIdAndReviewerId(any(), any())).thenReturn(false);
            when(reviewRepository.save(any())).thenAnswer(invocation -> {
                JobReview review = invocation.getArgument(0);
                review.setId(1L);
                review.setCreatedAt(LocalDateTime.now());
                return review;
            });

            JobReviewResponse response = jobReviewService.createReview(mockReviewer.getId(), request);

            assertThat(response).isNotNull();
            verify(reviewRepository).save(any());
        }

        @Test
        @DisplayName("Should fail when duplicate review exists")
        void shouldFailWhenDuplicateReviewExists() {
            CreateJobReviewRequest request = createValidReviewRequest();
            // Ensure validation passes up to duplicate check
            mockApplication.setStatus(ShortTermApplicationStatus.COMPLETED);
            mockJob.setStatus(ShortTermJobStatus.PAID);

            when(userRepository.findById(mockReviewer.getId())).thenReturn(Optional.of(mockReviewer));
            when(applicationRepository.findById(request.getApplicationId())).thenReturn(Optional.of(mockApplication));
            when(reviewRepository.existsByApplicationIdAndReviewerId(any(), any())).thenReturn(true);

            assertThatThrownBy(() -> jobReviewService.createReview(mockReviewer.getId(), request))
                    .isInstanceOf(BadRequestException.class);
        }
    }

    // ==================== REVIEW ELIGIBILITY VALIDATION TESTS ====================

    @Nested
    @DisplayName("Review Eligibility Validation Tests")
    class ReviewEligibilityTests {

        @Test
        @DisplayName("Should check review eligibility correctly - can write")
        void shouldReturnTrueWhenCanWriteReview() {
            // canWriteReview checks: app.status==COMPLETED, job.status==PAID, user is involved
            mockApplication.setStatus(ShortTermApplicationStatus.COMPLETED);
            mockJob.setStatus(ShortTermJobStatus.PAID);

            when(applicationRepository.findById(mockApplication.getId())).thenReturn(Optional.of(mockApplication));

            boolean canReview = jobReviewService.canWriteReview(mockReviewer.getId(), mockApplication.getId());

            assertThat(canReview).isTrue();
        }

        @Test
        @DisplayName("Should return false for review eligibility when already reviewed")
        void shouldReturnFalseWhenAlreadyReviewed() {
            mockApplication.setStatus(ShortTermApplicationStatus.COMPLETED);
            mockJob.setStatus(ShortTermJobStatus.PAID);

            when(applicationRepository.findById(mockApplication.getId())).thenReturn(Optional.of(mockApplication));
            when(reviewRepository.existsByApplicationIdAndReviewerId(mockApplication.getId(), mockReviewer.getId())).thenReturn(true);

            boolean canReview = jobReviewService.canWriteReview(mockReviewer.getId(), mockApplication.getId());

            assertThat(canReview).isFalse();
        }
    }

    // ==================== RATING SUMMARY TESTS ====================

    @Nested
    @DisplayName("Rating Summary Tests")
    class RatingSummaryTests {

        @Test
        @DisplayName("Should calculate user rating summary correctly")
        void shouldCalculateRatingSummaryCorrectly() {
            when(userRepository.findById(mockReviewee.getId())).thenReturn(Optional.of(mockReviewee));
            when(reviewRepository.getAverageRatingForUser(mockReviewee.getId()))
                    .thenReturn(new BigDecimal("4.5"));
            when(reviewRepository.countPublicReviewsForUser(mockReviewee.getId())).thenReturn(10L);
            when(applicationRepository.countCompletedByUser(mockReviewee.getId())).thenReturn(15L);
            when(applicationRepository.countByUserId(mockReviewee.getId())).thenReturn(20L);
            when(reviewRepository.getRatingBreakdownForUser(mockReviewee.getId()))
                    .thenReturn(Collections.emptyList());
            when(reviewRepository.getAverageSpecificRatingsForUser(mockReviewee.getId()))
                    .thenReturn(new Object[]{4.3, 4.7, 4.2, 4.8});

            UserRatingSummary summary = jobReviewService.getUserRatingSummary(mockReviewee.getId());

            assertThat(summary).isNotNull();
            assertThat(summary.getTotalReviews()).isEqualTo(10);
            assertThat(summary.getTotalCompletedJobs()).isEqualTo(15);
        }

        @Test
        @DisplayName("Should handle user with no reviews")
        void shouldHandleUserWithNoReviews() {
            when(userRepository.findById(mockReviewee.getId())).thenReturn(Optional.of(mockReviewee));
            when(reviewRepository.getAverageRatingForUser(mockReviewee.getId())).thenReturn(null);
            when(reviewRepository.countPublicReviewsForUser(mockReviewee.getId())).thenReturn(0L);
            when(applicationRepository.countCompletedByUser(mockReviewee.getId())).thenReturn(0L);
            when(applicationRepository.countByUserId(mockReviewee.getId())).thenReturn(0L);
            when(reviewRepository.getRatingBreakdownForUser(mockReviewee.getId()))
                    .thenReturn(Collections.emptyList());
            when(reviewRepository.getAverageSpecificRatingsForUser(mockReviewee.getId()))
                    .thenReturn(new Object[]{null, null, null, null});

            UserRatingSummary summary = jobReviewService.getUserRatingSummary(mockReviewee.getId());

            assertThat(summary).isNotNull();
            assertThat(summary.getTotalReviews()).isEqualTo(0);
            assertThat(summary.getTotalCompletedJobs()).isEqualTo(0);
        }
    }

    // ==================== HELPER METHODS ====================

    private CreateJobReviewRequest createValidReviewRequest() {
        CreateJobReviewRequest request = new CreateJobReviewRequest();
        request.setApplicationId(mockApplication.getId());
        request.setRating(5);
        request.setComment("This is a valid review comment with enough content for validation.");
        request.setStrengths("Great communication and quality work");
        request.setImprovements("Could improve on timeline estimation");
        request.setRecommendations("Highly recommended for future projects");
        request.setCommunicationRating(4);
        request.setQualityRating(5);
        request.setTimelinessRating(4);
        request.setProfessionalismRating(5);
        request.setIsPublic(true);
        return request;
    }
}
