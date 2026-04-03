package com.exe.skillverse_backend.business_service.service;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.business_service.entity.Dispute;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.business_service.entity.TrustScore;
import com.exe.skillverse_backend.business_service.repository.DisputeRepository;
import com.exe.skillverse_backend.business_service.repository.JobReviewRepository;
import com.exe.skillverse_backend.business_service.repository.ShortTermJobApplicationRepository;
import com.exe.skillverse_backend.business_service.repository.ShortTermJobRepository;
import com.exe.skillverse_backend.business_service.repository.TrustScoreRepository;
import com.exe.skillverse_backend.business_service.service.impl.TrustScoreServiceImpl;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrustScoreServiceImplTest {

    @Mock
    private TrustScoreRepository trustScoreRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ShortTermJobRepository shortTermJobRepository;

    @Mock
    private ShortTermJobApplicationRepository applicationRepository;

    @Mock
    private JobReviewRepository reviewRepository;

    @Mock
    private DisputeRepository disputeRepository;

    private TrustScoreServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TrustScoreServiceImpl(
                trustScoreRepository,
                userRepository,
                shortTermJobRepository,
                applicationRepository,
                reviewRepository,
                disputeRepository);

        lenient().when(trustScoreRepository.save(any(TrustScore.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("recalculateScore should aggregate recruiter, worker, review and dispute metrics")
    void recalculateScore_ShouldAggregateMetrics() {
        Long userId = 11L;
        User user = User.builder()
                .id(userId)
                .createdAt(LocalDateTime.now().minusDays(30))
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(trustScoreRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(shortTermJobRepository.countByRecruiterProfileUserId(userId)).thenReturn(3L);
        when(shortTermJobRepository.countCompletedByRecruiterProfile(userId)).thenReturn(2L);
        when(applicationRepository.countByUserId(userId)).thenReturn(2L);
        when(applicationRepository.countCompletedByUser(userId)).thenReturn(1L);
        when(reviewRepository.getAverageRatingForUser(userId)).thenReturn(new BigDecimal("4.50"));
        when(reviewRepository.countByUserId(userId)).thenReturn(7L);
        when(disputeRepository.findByInitiatorId(userId)).thenReturn(List.of(new Dispute(), new Dispute()));
        when(disputeRepository.findByRespondentId(userId)).thenReturn(List.of(new Dispute()));

        TrustScore result = service.recalculateScore(userId);

        assertEquals(5, result.getTotalJobs());
        assertEquals(3, result.getCompletedJobs());
        assertEquals(new BigDecimal("0.6000"), result.getCompletionRate());
        assertEquals(new BigDecimal("4.50"), result.getAvgRating());
        assertEquals(7, result.getTotalReviews());
        assertEquals(3, result.getDisputedJobs());
        assertEquals(new BigDecimal("0.6000"), result.getDisputeRate());
        assertTrue(result.getAccountAgeDays() >= 29);
        assertEquals(BigDecimal.ZERO, result.getResponseTimeHours());
    }

    @Test
    @DisplayName("recalculateScore should throw when the user does not exist")
    void recalculateScore_ShouldThrowWhenUserMissing() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.recalculateScore(99L));
    }

    @Test
    @DisplayName("triggerRecalculationOnJobComplete should recalculate both participants")
    void triggerRecalculationOnJobComplete_ShouldRecalculateBothParticipants() {
        TrustScoreServiceImpl spyService = spy(service);
        doReturn(TrustScore.builder().build()).when(spyService).recalculateScore(anyLong());

        spyService.triggerRecalculationOnJobComplete(1L, 2L);

        verify(spyService).recalculateScore(1L);
        verify(spyService).recalculateScore(2L);
    }
}
