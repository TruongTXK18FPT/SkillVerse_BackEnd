package com.exe.skillverse_backend.business_service.service;

import com.exe.skillverse_backend.business_service.dto.request.CreateJobBoostRequest;
import com.exe.skillverse_backend.business_service.dto.response.JobBoostResponse;
import com.exe.skillverse_backend.business_service.entity.JobBoost;
import com.exe.skillverse_backend.business_service.entity.JobPosting;
import com.exe.skillverse_backend.business_service.entity.RecruiterProfile;
import com.exe.skillverse_backend.business_service.entity.enums.JobBoostStatus;
import com.exe.skillverse_backend.business_service.entity.enums.JobStatus;
import com.exe.skillverse_backend.business_service.repository.JobBoostRepository;
import com.exe.skillverse_backend.business_service.repository.JobPostingRepository;
import com.exe.skillverse_backend.business_service.service.impl.JobBoostServiceImpl;
import com.exe.skillverse_backend.premium_service.entity.FeatureType;
import com.exe.skillverse_backend.premium_service.service.RecruiterSubscriptionService;
import com.exe.skillverse_backend.premium_service.service.UsageLimitService;
import com.exe.skillverse_backend.shared.exception.BadRequestException;
import com.exe.skillverse_backend.shared.exception.ForbiddenException;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for JobBoostService
 */
@ExtendWith(MockitoExtension.class)
class JobBoostServiceTest {

    @Mock
    private JobBoostRepository jobBoostRepository;

    @Mock
    private JobPostingRepository jobPostingRepository;

    @Mock
    private RecruiterSubscriptionService recruiterSubscriptionService;

    @Mock
    private UsageLimitService usageLimitService;

    @InjectMocks
    private JobBoostServiceImpl jobBoostService;

    private RecruiterProfile recruiterProfile;
    private JobPosting openJob;
    private JobBoost existingBoost;

    @BeforeEach
    void setUp() {
        // Create test recruiter profile
        recruiterProfile = RecruiterProfile.builder()
                .userId(1L)
                .companyName("Test Company")
                .user(new com.exe.skillverse_backend.auth_service.entity.User() {{
                    setId(100L);
                    setEmail("recruiter@test.com");
                }})
                .build();

        // Create test job posting
        openJob = JobPosting.builder()
                .id(1L)
                .title("Senior Java Developer")
                .description("We are looking for a senior Java developer")
                .requiredSkills("[\"java\",\"spring boot\"]")
                .minBudget(new BigDecimal("1000"))
                .maxBudget(new BigDecimal("2000"))
                .deadline(LocalDate.now().plusDays(30))
                .isRemote(true)
                .status(JobStatus.OPEN)
                .applicantCount(0)
                .recruiterProfile(recruiterProfile)
                .createdAt(LocalDateTime.now())
                .build();

        // Create existing boost
        existingBoost = JobBoost.builder()
                .id(1L)
                .jobPosting(openJob)
                .recruiterId(100L)
                .boostStatus(JobBoostStatus.ACTIVE)
                .startedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(7))
                .impressions(100)
                .clicks(10)
                .applications(5)
                .createdAt(LocalDateTime.now())
                .createdBy(100L)
                .build();
    }

    @Test
    void createBoost_Success() {
        // Given
        when(recruiterSubscriptionService.hasActiveRecruiterSubscription(100L)).thenReturn(true);
        when(usageLimitService.getUserUsage(eq(100L), eq(FeatureType.JOB_BOOST_MONTHLY)))
                .thenReturn(new com.exe.skillverse_backend.premium_service.dto.response.FeatureLimitInfo() {{
                    setLimit(5);
                    setCurrentUsage(2);
                    setRemaining(3);
                    setIsUnlimited(false);
                }});
        doNothing().when(usageLimitService).checkQuotaOnly(100L, FeatureType.JOB_BOOST_MONTHLY);
        when(jobPostingRepository.findByIdAndRecruiterProfileUserId(1L, 100L))
                .thenReturn(Optional.of(openJob));
        when(jobBoostRepository.findByJobPostingId(1L)).thenReturn(Optional.empty());
        when(jobBoostRepository.saveAndFlush(any(JobBoost.class))).thenAnswer(invocation -> {
            JobBoost boost = invocation.getArgument(0);
            boost.setId(1L);
            return boost;
        });

        CreateJobBoostRequest request = CreateJobBoostRequest.builder()
                .jobId(1L)
                .durationDays(7)
                .build();

        // When
        JobBoostResponse response = jobBoostService.createBoost(100L, request);

        // Then
        assertNotNull(response);
        assertEquals(JobBoostStatus.ACTIVE, response.getBoostStatus());
        verify(usageLimitService).checkQuotaOnly(100L, FeatureType.JOB_BOOST_MONTHLY);
    }

    @Test
    void createBoost_NoPremium_ThrowsForbidden() {
        // Given
        when(recruiterSubscriptionService.hasActiveRecruiterSubscription(100L)).thenReturn(false);

        CreateJobBoostRequest request = CreateJobBoostRequest.builder()
                .jobId(1L)
                .durationDays(7)
                .build();

        // When/Then
        ForbiddenException exception = assertThrows(ForbiddenException.class,
                () -> jobBoostService.createBoost(100L, request));

        assertTrue(exception.getMessage().contains("Premium"));
    }

    @Test
    void createBoost_JobNotFound_ThrowsNotFound() {
        // Given
        when(recruiterSubscriptionService.hasActiveRecruiterSubscription(100L)).thenReturn(true);
        when(usageLimitService.getUserUsage(eq(100L), eq(FeatureType.JOB_BOOST_MONTHLY)))
                .thenReturn(new com.exe.skillverse_backend.premium_service.dto.response.FeatureLimitInfo() {{
                    setLimit(5);
                    setCurrentUsage(2);
                    setRemaining(3);
                    setIsUnlimited(false);
                }});
        when(jobPostingRepository.findByIdAndRecruiterProfileUserId(1L, 100L))
                .thenReturn(Optional.empty());

        CreateJobBoostRequest request = CreateJobBoostRequest.builder()
                .jobId(1L)
                .durationDays(7)
                .build();

        // When/Then
        assertThrows(NotFoundException.class,
                () -> jobBoostService.createBoost(100L, request));
    }

    @Test
    void createBoost_ClosedJob_ThrowsBadRequest() {
        // Given
        openJob.setStatus(JobStatus.CLOSED);
        when(recruiterSubscriptionService.hasActiveRecruiterSubscription(100L)).thenReturn(true);
        when(usageLimitService.getUserUsage(eq(100L), eq(FeatureType.JOB_BOOST_MONTHLY)))
                .thenReturn(new com.exe.skillverse_backend.premium_service.dto.response.FeatureLimitInfo() {{
                    setLimit(5);
                    setCurrentUsage(2);
                    setRemaining(3);
                    setIsUnlimited(false);
                }});
        when(jobPostingRepository.findByIdAndRecruiterProfileUserId(1L, 100L))
                .thenReturn(Optional.of(openJob));

        CreateJobBoostRequest request = CreateJobBoostRequest.builder()
                .jobId(1L)
                .durationDays(7)
                .build();

        // When/Then
        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> jobBoostService.createBoost(100L, request));

        assertTrue(exception.getMessage().contains("đóng"));
    }

    @Test
    void createBoost_AlreadyBoosted_ThrowsBadRequest() {
        // Given
        when(recruiterSubscriptionService.hasActiveRecruiterSubscription(100L)).thenReturn(true);
        when(usageLimitService.getUserUsage(eq(100L), eq(FeatureType.JOB_BOOST_MONTHLY)))
                .thenReturn(new com.exe.skillverse_backend.premium_service.dto.response.FeatureLimitInfo() {{
                    setLimit(5);
                    setCurrentUsage(2);
                    setRemaining(3);
                    setIsUnlimited(false);
                }});
        when(jobPostingRepository.findByIdAndRecruiterProfileUserId(1L, 100L))
                .thenReturn(Optional.of(openJob));
        when(jobBoostRepository.findByJobPostingId(1L))
                .thenReturn(Optional.of(existingBoost));

        CreateJobBoostRequest request = CreateJobBoostRequest.builder()
                .jobId(1L)
                .durationDays(7)
                .build();

        // When/Then
        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> jobBoostService.createBoost(100L, request));

        assertTrue(exception.getMessage().contains("đã dùng lượt boost"));
    }

    @Test
    void cancelBoost_Success() {
        // Given
        when(jobBoostRepository.findById(1L)).thenReturn(Optional.of(existingBoost));
        when(jobBoostRepository.save(any(JobBoost.class))).thenReturn(existingBoost);

        // When
        JobBoostResponse response = jobBoostService.cancelBoost(100L, 1L);

        // Then
        assertNotNull(response);
        assertEquals(JobBoostStatus.CANCELLED, response.getBoostStatus());
    }

    @Test
    void cancelBoost_NotOwner_ThrowsForbidden() {
        // Given
        when(jobBoostRepository.findById(1L)).thenReturn(Optional.of(existingBoost));

        // When/Then
        assertThrows(ForbiddenException.class,
                () -> jobBoostService.cancelBoost(999L, 1L));
    }

    @Test
    void getActiveBoostedJobIds_ReturnsBoostedJobIds() {
        // Given
        when(jobBoostRepository.findAllActiveBoostedJobIds()).thenReturn(List.of(1L, 2L, 3L));

        // When
        List<Long> ids = jobBoostService.getActiveBoostedJobIds();

        // Then
        assertEquals(3, ids.size());
        assertTrue(ids.contains(1L));
        assertTrue(ids.contains(2L));
        assertTrue(ids.contains(3L));
    }

    @Test
    void hasActiveBoost_ReturnsTrueForBoostedJob() {
        // Given
        when(jobBoostRepository.hasActiveBoost(1L)).thenReturn(true);

        // When
        boolean hasBoost = jobBoostService.hasActiveBoost(1L);

        // Then
        assertTrue(hasBoost);
    }

    @Test
    void hasActiveBoost_ReturnsFalseForNonBoostedJob() {
        // Given
        when(jobBoostRepository.hasActiveBoost(1L)).thenReturn(false);

        // When
        boolean hasBoost = jobBoostService.hasActiveBoost(1L);

        // Then
        assertFalse(hasBoost);
    }

    @Test
    void processExpiredBoosts_UpdatesExpiredBoosts() {
        // Given
        JobBoost expiredBoost = JobBoost.builder()
                .id(2L)
                .jobPosting(openJob)
                .recruiterId(100L)
                .boostStatus(JobBoostStatus.ACTIVE)
                .startedAt(LocalDateTime.now().minusDays(10))
                .expiresAt(LocalDateTime.now().minusDays(1))
                .createdAt(LocalDateTime.now().minusDays(10))
                .build();

        when(jobBoostRepository.findBoostsExpiringBefore(any(LocalDateTime.class)))
                .thenReturn(List.of(expiredBoost));
        when(jobBoostRepository.save(any(JobBoost.class))).thenReturn(expiredBoost);

        // When
        jobBoostService.processExpiredBoosts();

        // Then
        verify(jobBoostRepository).save(argThat(boost ->
                boost.getBoostStatus() == JobBoostStatus.EXPIRED));
    }

    @Test
    void getAvailableBoostQuota_WithUnlimited_ReturnsMaxInt() {
        // Given
        when(recruiterSubscriptionService.hasActiveRecruiterSubscription(100L)).thenReturn(true);
        when(usageLimitService.getUserUsage(eq(100L), eq(FeatureType.JOB_BOOST_MONTHLY)))
                .thenReturn(new com.exe.skillverse_backend.premium_service.dto.response.FeatureLimitInfo() {{
                    setLimit(100);
                    setCurrentUsage(50);
                    setRemaining(50);
                    setIsUnlimited(true);
                }});

        // When
        int quota = jobBoostService.getAvailableBoostQuota(100L);

        // Then
        assertEquals(Integer.MAX_VALUE, quota);
    }

    @Test
    void getAvailableBoostQuota_NoSubscription_ReturnsZero() {
        // Given
        when(recruiterSubscriptionService.hasActiveRecruiterSubscription(100L)).thenReturn(false);

        // When
        int quota = jobBoostService.getAvailableBoostQuota(100L);

        // Then
        assertEquals(0, quota);
    }
}
